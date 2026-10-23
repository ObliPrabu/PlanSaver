#!/usr/bin/env python3
"""Refresh deals-data/catalog.json - phones, cars and car insurers - using only free, keyless sources (stdlib only).

catalog.json holds only NAMES and page addresses; every number in it is filled in by this robot and carries the page it
came from (`sources`) and the date (`updated`). Nothing is hand-researched, and a field is only overwritten when a new
value was actually found, so a blocked request can never wipe data.
  * cars:     fuel economy from the US government's free fueleconomy.gov API (official EPA data); starting price from the
              maker's own page or its own-domain search results when they show one.
  * phones:   starting price from the maker's own page; display, battery, chip and storage from sentences on that page,
              from the maker's own search results, and from gsmarena.com / phonearena.com snippets.
  * insurers: average monthly cost from search snippets on a short list of well-known insurance-review sites; a number is
              only taken when it sits right next to the insurer's name and says "full coverage" or "minimum / liability".
Each run handles the LIMIT entries that were checked longest ago, so the whole catalog is covered over several runs and a
run stays well inside the workflow's time limit. Usage: python tools/refresh_catalog.py [path/to/catalog.json]
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import date

import refresh_deals as rd  # fetch, sentences, tidy, search, same_site, domain

PATH = sys.argv[1] if len(sys.argv) > 1 else "deals-data/catalog.json"
LIMIT = int(os.environ.get("CATALOG_LIMIT", "12"))
EPA_YEAR = int(os.environ.get("CATALOG_YEAR", "2026"))
PAUSE = 2  # seconds between requests
SPEC_SITES = ("gsmarena.com", "phonearena.com")
INSURANCE_SITES = ("nerdwallet.com", "bankrate.com", "forbes.com", "valuepenguin.com", "autoinsurance.org",
                   "insurify.com", "thezebra.com", "consumerreports.org", "policygenius.com")


def today():
    return date.today().isoformat()


def money(s):
    return float(s.replace(",", ""))


# ---------------------------------------------------------------- prices

PRICE_WORDS = re.compile(r"(?:Starting\s+(?:MSRP|at|from)|MSRP\s+from|From|Starts\s+at)\s*:?\s*\$\s?(\d[\d,]*(?:\.\d\d)?)", re.I)


def price_from_text(text, low, high):
    """The first 'From $899' / 'Starting MSRP $29,300' style price in the text that is a believable price, else None."""
    for m in PRICE_WORDS.finditer(text):
        value = money(m.group(1))
        if low <= value <= high:
            return value
    return None


# ---------------------------------------------------------------- phones

DISPLAY = re.compile(r"\b(\d\.\d{1,2})[- ]?(?:inch|in\b|\")", re.I)
BATTERY = re.compile(r"\b([2-7],?\d{3})\s?mAh\b", re.I)
CHIP = re.compile(r"\b(A\d{2}(?: Pro| Bionic)?|Snapdragon 8 (?:Elite|Gen \d)(?: Gen \d| for Galaxy)?|Tensor G\d)\b")
STORAGE = re.compile(r"\b(\d{1,4})\s?(GB|TB)\b")


def mentions(sentence, name, all_names):
    """True when the sentence names this phone, not only a longer phone whose name starts with it (a sentence about the
    'iPhone 17 Pro' is not a sentence about the 'iPhone 17')."""
    low = sentence.lower()
    longer = [n.lower() for n in all_names if n != name and n.lower().startswith(name.lower())]
    for m in re.finditer(re.escape(name.lower()), low):
        tail = low[m.start():]
        if not any(tail.startswith(n) for n in longer):
            return True
    return False


def specs_from_sentences(sentences):
    """Display (inches), battery (mAh), chip and storage options found in a phone's sentences."""
    out = {}
    storage = []
    for s in sentences:
        if "display" not in out:
            m = DISPLAY.search(s)
            if m and 4.5 <= float(m.group(1)) <= 8.5 and re.search(r"display|screen|inch", s, re.I):
                out["display"] = float(m.group(1))
        if "batteryMah" not in out:
            m = BATTERY.search(s)
            if m:
                out["batteryMah"] = int(m.group(1).replace(",", ""))
        if "chip" not in out:
            m = CHIP.search(s)
            if m:
                out["chip"] = m.group(1).strip()
        if re.search(r"storage|capacity|memory|\bGB\b|\bTB\b", s, re.I):
            for n, unit in STORAGE.findall(s):
                label = f"{n}{unit}"
                if unit == "GB" and int(n) not in (64, 128, 256, 512):
                    continue
                if unit == "TB" and int(n) not in (1, 2):
                    continue
                if label not in storage:
                    storage.append(label)
    if storage:
        order = lambda x: int(x[:-2]) * (1024 if x.endswith("TB") else 1)
        out["storage"] = ", ".join(sorted(storage, key=order))
    return out


def refresh_phone(entry, all_names, shared_page=False):
    found = {}
    sources = {}
    sentences = []  # (sentence, url)
    try:
        page = rd.fetch(entry["url"])
        if not shared_page:  # a page that sells several phones can't say which "From $X" is this one's
            price = price_from_text(page, 150, 2500)
            if price:
                found["price"], sources["price"] = price, entry["url"]
        for s in rd.sentences(page):
            if mentions(s, entry["name"], all_names):  # specs only from sentences that name this phone
                sentences.append((s, entry["url"]))
    except Exception as e:
        print(f"  page failed: {e}")
    time.sleep(PAUSE)
    for url, snippet in rd.search(f"{entry['name']} specs display battery chip storage"):
        host_ok = rd.same_site(url, entry["url"]) or any(rd.domain(url).endswith(d) for d in SPEC_SITES)
        if host_ok and url.startswith("https://") and mentions(snippet, entry["name"], all_names):
            sentences.append((rd.tidy(snippet), url))
    for field, value in specs_from_sentences([s for s, _ in sentences]).items():
        found[field] = value
        sources[field] = next((u for s, u in sentences if value_in(field, value, s)), entry["url"])
    return found, sources


def value_in(field, value, sentence):
    """Which sentence a spec value came from (so its source can be shown)."""
    return bool(specs_from_sentences([sentence]).get(field) == value)


# ---------------------------------------------------------------- cars (EPA)

def get_json(url):
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": rd.UA})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def first_option_id(make, model, year):
    url = ("https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year=%d&make=%s&model=%s"
           % (year, urllib.parse.quote(make), urllib.parse.quote(model)))
    items = get_json(url).get("menuItem")
    if not items:
        return None
    return (items[0] if isinstance(items, list) else items)["value"]


def epa_fields(vehicle):
    """Fuel economy fields from one fueleconomy.gov vehicle record. Electric cars report MPGe and a range in miles."""
    electric = vehicle.get("fuelType1") == "Electricity" or vehicle.get("fuelType") == "Electricity"
    out = {
        "mpgCombined": int(vehicle["comb08"]), "mpgCity": int(vehicle["city08"]), "mpgHighway": int(vehicle["highway08"]),
        "mpgUnit": "MPGe" if electric else "MPG", "fuelType": vehicle.get("fuelType", ""),
        "bodyType": vehicle.get("VClass", ""), "annualFuelCost": int(vehicle["fuelCost08"]),
    }
    if electric and int(vehicle.get("range") or 0) > 0:
        out["rangeMiles"] = int(vehicle["range"])
    return out


def refresh_car(entry):
    found, sources = {}, {}
    for year in (EPA_YEAR, EPA_YEAR - 1):
        try:
            vid = first_option_id(entry["make"], entry["epaModel"], year)
            if not vid:
                continue
            vehicle = get_json(f"https://www.fueleconomy.gov/ws/rest/vehicle/{vid}")
            found.update(epa_fields(vehicle))
            found["modelYear"] = int(vehicle.get("year") or year)
            src = f"https://www.fueleconomy.gov/feg/Find.do?action=sbsSelect&id={vid}"
            sources.update({k: src for k in list(found)})
            break
        except Exception as e:
            print(f"  EPA failed: {e}")
    time.sleep(PAUSE)
    try:
        price = price_from_text(rd.fetch(entry["url"]), 12000, 250000)
        if price:
            found["price"], sources["price"] = price, entry["url"]
    except Exception as e:
        print(f"  page failed: {e}")
    if "price" not in found:
        time.sleep(PAUSE)
        for url, snippet in rd.search(f"{entry['name']} {EPA_YEAR} starting MSRP"):
            if rd.same_site(url, entry["url"]) and url.startswith("https://"):
                price = price_from_text(snippet, 12000, 250000)
                if price:
                    found["price"], sources["price"] = price, url
                    break
    return found, sources


# ---------------------------------------------------------------- insurers

def insurer_amounts(sentence, name, context=""):
    """{'full': monthly $, 'min': monthly $} for amounts that sit right after the insurer's name in the sentence.
    Yearly amounts are divided by 12. Only 'full coverage' and 'minimum / liability' amounts are taken."""
    out = {}
    low_all = sentence.lower()
    ctx = context.lower() + " "  # the sentences just before may carry the "full coverage" label
    for m in re.finditer(re.escape(name) + r"[^$]{0,40}\$\s?(\d[\d,]*(?:\.\d\d)?)([^$]{0,50})", sentence, re.I):
        amount = money(m.group(1))
        before, tail = ctx + low_all[:m.end()], m.group(2).lower()
        if re.search(r"month|/mo\b|monthly", low_all):
            monthly = amount
        elif re.search(r"year|annual|/yr\b", low_all):
            monthly = amount / 12
        else:
            continue
        if re.search(r"year|annual|/yr\b", tail) and not re.search(r"month|/mo\b", tail):
            monthly = amount / 12  # the amount itself says "per year", whatever else the sentence mentions
        if not 20 <= monthly <= 800:
            continue
        # The coverage label is the nearest one at or before the amount (else the first one after it).
        labels = [(mm.start(), "full") for mm in re.finditer(r"full[- ]coverage", before)] + \
                 [(mm.start(), "min") for mm in re.finditer(r"minimum|liability|state[- ]required", before)]
        if not labels:
            labels = [(mm.start(), "full") for mm in re.finditer(r"full[- ]coverage", tail)] + \
                     [(mm.start(), "min") for mm in re.finditer(r"minimum|liability|state[- ]required", tail)]
        kind = max(labels)[1] if labels else None
        if kind and kind not in out:
            out[kind] = round(monthly, 2)
    return out


_PAGES = {}


def page_sentences(url):
    """Sentences of a page, fetched once per run ([] when the page can't be read)."""
    if url not in _PAGES:
        try:
            _PAGES[url] = rd.sentences(rd.fetch(url))
        except Exception as e:
            print(f"  page failed: {url}: {e}")
            _PAGES[url] = []
        time.sleep(PAUSE)
    return _PAGES[url]


def refresh_insurer(entry, pages):
    """Average monthly cost from the review pages listed in catalog.json. Only sentences that name the current year
    count, so old tables on the same pages can't leak in."""
    found, sources = {}, {}
    year = str(date.today().year)
    for url in pages:
        sents = page_sentences(url)
        for i, s in enumerate(sents):
            # "Average" / "median" figures only: a single quote for one imagined driver is not comparable to them.
            if year not in s or entry["name"].lower() not in s.lower() or not re.search(r"\b(average|averages|median)\b", s, re.I):
                continue
            amounts = insurer_amounts(rd.tidy(s), entry["name"], " ".join(sents[max(0, i - 2):i]))
            for kind, field in (("full", "fullMonthly"), ("min", "minMonthly")):
                if kind in amounts and field not in found:
                    found[field], sources[field] = amounts[kind], url
    return found, sources


# ---------------------------------------------------------------- main

def apply(entry, found, sources):
    """Write found values into the entry; returns True when anything changed."""
    changed = False
    for field, value in found.items():
        if entry.get(field) != value:
            entry[field] = value
            changed = True
        if sources.get(field) and entry.setdefault("sources", {}).get(field) != sources[field]:
            entry["sources"][field] = sources[field]
            changed = True
    if changed:
        entry["updated"] = today()
    return changed


def main():
    with open(PATH) as f:
        data = json.load(f)
    work = [(kind, e) for kind in ("phones", "cars", "insurers") for e in data.get(kind, [])]
    work.sort(key=lambda w: w[1].get("checked", ""))
    phone_names = [p["name"] for p in data.get("phones", [])]
    changed = False
    for kind, entry in work[:LIMIT]:
        print(f"{kind[:-1]}: {entry['name']}")
        try:
            if kind == "phones":
                found, sources = refresh_phone(entry, phone_names,
                                               shared_page=sum(p["url"] == entry["url"] for p in data["phones"]) > 1)
            elif kind == "cars":
                found, sources = refresh_car(entry)
            else:
                found, sources = refresh_insurer(entry, data.get("insurancePages", []))
        except Exception as e:  # keep the old entry on any failure
            print(f"  failed: {e}")
            found, sources = {}, {}
        if found:
            print("  found:", {k: v for k, v in found.items()})
        changed |= apply(entry, found, sources)
        if entry.get("checked") != today():
            entry["checked"] = today()
            changed = True
        time.sleep(PAUSE)
    if changed:
        data["updated"] = today()
        with open(PATH, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("catalog.json updated")
    else:
        print("no change")


if __name__ == "__main__":
    main()
