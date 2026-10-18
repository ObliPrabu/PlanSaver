#!/usr/bin/env python3
"""Refresh deals-data/deals.json using only free, keyless sources (stdlib only).

For each carrier already in the file: read the carrier's own deals page and a DuckDuckGo HTML search (Bing if DuckDuckGo fails), keep the best
deal-sounding sentence that came from the carrier's OWN domain, and store it. Any failure keeps the old entry, so a
blocked request can never wipe the deals. "phones" is never guessed. A "plans" scope is kept only while the new text still
names one of its plans. freePhone is re-derived when the deal text changes; "updated" on each entry is the date its text
last changed. A new text must score clearly higher than the stored one, so candidates can't flip-flop.
Usage: python tools/refresh_deals.py [path/to/deals.json]
"""
import base64
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import date
from html.parser import HTMLParser

PATH = sys.argv[1] if len(sys.argv) > 1 else "deals-data/deals.json"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
KEYWORDS = ("free", "off", "save", "switch", "trade", "bill credit", "per month", "/mo", "iphone", "galaxy", "pixel")
MIN_SCORE = 5
MARGIN = 2  # a new deal text must beat the stored one by this much
MAX_LEN = 240


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9",
                                               "Accept": "text/html,application/xhtml+xml"})
    with urllib.request.urlopen(req, timeout=20) as r:
        if r.status != 200:
            raise IOError(r.status)
        return r.read(2_000_000).decode("utf-8", errors="replace")


class TextOnly(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts, self.skip = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript", "svg"):
            self.skip += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript", "svg") and self.skip:
            self.skip -= 1

    def handle_data(self, data):
        if not self.skip:
            self.parts.append(data)


def clean(text):
    text = html.unescape(re.sub(r"<[^>]+>", " ", text))
    text = re.sub(r"[\x00-\x1f\x7f]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def sentences(page):
    p = TextOnly()
    p.feed(page)
    text = clean(" ".join(p.parts))
    return [s.strip() for s in re.split(r"(?<=[.!])\s+", text) if 25 <= len(s) <= MAX_LEN]


# Button / label text that carrier pages put in front of the actual offer.
UI_NOISE = re.compile(
    r"\b(see offer details|shop now|get offer|learn more|check compatibility|view broadband facts label|"
    r"limited-time offer|new & existing customers|learn how to get this offer)\b[:.]?\s*", re.I)
GENERIC = ("shop our best", "right now", "top offers", "all deals", "find a deal", "explore")


def tidy(s):
    return UI_NOISE.sub("", s).strip(" -|")


def score(s):
    low = s.lower()
    # A real offer names a price or says free, and is not a generic shop banner.
    if ("$" not in s and "free" not in low) or any(g in low for g in GENERIC):
        return 0
    n = sum(1 for k in KEYWORDS if k in low) + (2 if "$" in s else 0)
    if is_noise(low):
        return 0
    return n


def is_noise(low):
    """Legal/boilerplate and product-listing scraps ("Rated 4.1 out of 5 stars ... Quick view") are not offers."""
    return any(b in low for b in ("cookie", "privacy", "terms", "sign in", "log in", "javascript",
                                  "quick view", "out of 5", "stars", "reviews", "compare ", "price was",
                                  "add to cart", "sort by"))


# A price tied to a number of lines: "$25 per line for 4 lines", "4 lines for $100/mo", "2 lines $60".
LINE_PRICE = re.compile(
    r"\$\s?\d+(?:\.\d\d)?[^.]{0,40}\bper line\b[^.]{0,30}\b[2-9]\s*lines?\b"
    r"|\b[2-9]\s*lines?\b[^.$]{0,30}\$\s?\d+(?:\.\d\d)?"
    r"|\bper line\b[^.]{0,30}\b[2-9]\s*lines?\b[^.]{0,30}\$\s?\d+", re.I)


def lines_score(s):
    """0 unless the sentence gives a price for a specific number of lines (2+) and is not boilerplate."""
    if is_noise(s.lower()) or not LINE_PRICE.search(s):
        return 0
    return 1 + score(s)


PHONE_WORDS = re.compile(r"\b(phone|phones|iphone|galaxy|pixel|device)\b", re.I)
GIVEAWAY = re.compile(r"\bfree\b|\$\s?\d[\d,]*\s+off|bill credits?", re.I)
SWITCH_DEAL = re.compile(r"\b(trade[- ]in|switch)\b", re.I)
MONEY_OFF = re.compile(r"\$\s?\d[\d,]*\s*(off|per line|back)|up to \$\s?\d[\d,]*", re.I)


def offers_phone(text):
    """True when the text offers a free / discounted phone: names a phone and says free, $ off or bill credits, or
    pairs a trade-in/switch with a dollar amount off."""
    if PHONE_WORDS.search(text) and GIVEAWAY.search(text):
        return True
    return bool(SWITCH_DEAL.search(text) and MONEY_OFF.search(text))


def fragment_key(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def plans_still_apply(entry, text):
    """The 'plans' scope was written for the old offer; keep it only if the new text still names one of its plans."""
    key = fragment_key(text)
    return any(fragment_key(f) in key for f in entry.get("plans", []))


def domain(url):
    host = urllib.parse.urlparse(url).netloc.lower()
    return host[4:] if host.startswith("www.") else host


def same_site(url, base):
    d, b = domain(url), domain(base)
    return d == b or d.endswith("." + b) or b.endswith("." + d)


def ddg_results(query):
    """(url, snippet) pairs from DuckDuckGo's HTML page."""
    page = fetch("https://html.duckduckgo.com/html/?q=" + urllib.parse.quote_plus(query))
    out = []
    for m in re.finditer(r'class="result__a"[^>]*href="([^"]+)".*?class="result__snippet"[^>]*>(.*?)</a>', page, re.S):
        href = html.unescape(m.group(1))
        q = urllib.parse.parse_qs(urllib.parse.urlparse(href).query)
        url = q.get("uddg", [href])[0]
        out.append((url, clean(m.group(2))))
    return out


# Other things shoppers ask about. Each kind: (what the sentence must mention, label, extra requirement, needs a number).
EXTRAS = {
    "hotspot": (re.compile(r"\bhot-?spot\b|\btethering\b", re.I), "Hotspot", None, True),
    "international": (re.compile(r"\b(canada|mexico|international|roaming|abroad)\b", re.I), "International",
                      re.compile(r"\b(calling|calls|texting|messag\w*|data|roaming|minutes)\b", re.I), False),
    # Named services only: a bare word like "streaming" shows up in marketing copy and video-quality settings.
    "perks": (re.compile(r"\b(netflix|disney\+?|hulu|apple tv|apple one|peacock|youtube|max)\b", re.I), "Perks",
              re.compile(r"\b(included|on us|free|with|bundle\w*)\b", re.I), False),
    "slowdown": (re.compile(r"\b(deprioriti[sz]\w*|throttl\w*|slowed|reduced speeds?)\b|\bafter\s+\d+\s*gb\b", re.I),
                 "Slowdown", re.compile(r"\d", re.I), False),
}
# Legal fine print and marketing copy that mention the right words without saying anything useful.
EXTRA_REJECT = ("trademark", "registered", "aren't included", "isn't included", "not included", "defaults to",
                "see how", "built for", "terms and conditions", "all rights reserved", "fcc.gov", "plan facts")
ID_TOKEN = re.compile(r"\b(?=[A-Za-z0-9]*\d)(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{10,}\b")  # e.g. broadband-label IDs
FOOTNOTE = re.compile(r"^(?:\^|footnote\s*\d+)\s*", re.I)


def extra_score(kind, s):
    """0 unless the sentence is about [kind] and is not boilerplate; concrete numbers and prices rank higher."""
    pattern, _, also, needs_number = EXTRAS[kind]
    s = FOOTNOTE.sub("", s)
    low = s.lower()
    if is_noise(low) or any(g in low for g in GENERIC) or any(x in low for x in EXTRA_REJECT) or ID_TOKEN.search(s) \
            or not pattern.search(s):
        return 0
    if also and not also.search(s):
        return 0
    has_number = bool(re.search(r"\d", s))
    if needs_number and not has_number:
        return 0
    return 1 + (2 if has_number else 0) + (1 if "$" in s else 0)


def best_extra(kind, found):
    candidates = [(extra_score(kind, t), FOOTNOTE.sub("", t), u) for t, u in found]
    candidates = [c for c in candidates if c[0] > 0]
    return max(candidates, key=lambda c: (c[0], -len(c[1]))) if candidates else None


def update_extra(entry, kind, best):
    new_score, text, _ = best
    notes = entry.setdefault("extras", {})
    old = notes.get(kind, {}).get("text", "")
    if text == old or (old and new_score < extra_score(kind, old) + MARGIN):
        return False
    print(f"  new {kind} note: {text}")
    notes[kind] = {"text": text, "updated": date.today().isoformat()}
    return True


def bing_url(href):
    """Bing wraps results in bing.com/ck/a?...&u=a1<base64 of the real URL>; unwrap it."""
    m = re.search(r"[?&]u=a1([A-Za-z0-9_-]+)", href)
    if not m:
        return href
    b = m.group(1)
    try:
        return base64.urlsafe_b64decode(b + "=" * (-len(b) % 4)).decode("utf-8")
    except Exception:
        return href


def bing_parse(page):
    out = []
    for item in re.findall(r'<li class="b_algo".*?</li>', page, re.S):
        link = re.search(r'<a[^>]+href="(https?://[^"]+)"', item)
        snippet = re.search(r"<p[^>]*>(.*?)</p>", item, re.S)
        if link and snippet:
            out.append((bing_url(html.unescape(link.group(1))), clean(snippet.group(1))))
    return out


def bing_results(query):
    """(url, snippet) pairs from Bing's HTML page - the fallback when DuckDuckGo blocks us."""
    return bing_parse(fetch("https://www.bing.com/search?q=" + urllib.parse.quote_plus(query)))


def search(query):
    """Results from the first search engine that answers with something (DuckDuckGo, then Bing)."""
    for name, engine in (("DuckDuckGo", ddg_results), ("Bing", bing_results)):
        try:
            found = engine(query)
            if found:
                return found
            print(f"  {name} returned no results")
        except Exception as e:
            print(f"  {name} failed: {e}")
    return []


def gather(entry):
    """Every cleaned (text, url) sentence from the carrier's page and its own-domain search results."""
    base = entry["url"]
    out = []
    try:
        out += [(tidy(x), base) for x in sentences(fetch(base))]
    except Exception as e:
        print(f"  page failed: {e}")
    for query in (entry["carrier"] + " phone deals", entry["carrier"] + " plans price per line multiple lines",
                  entry["carrier"] + " unlimited plan hotspot data international roaming perks"):
        if "multiple lines" in query and best_lines(out):
            continue  # the page already gave a multi-line price; spare DuckDuckGo a request
        time.sleep(2)
        for url, snippet in search(query):
            snippet = tidy(snippet)
            if url.startswith("https://") and same_site(url, base) and 25 <= len(snippet) <= MAX_LEN:
                out.append((snippet, url))
    return out


def best_candidate(found):
    candidates = [(score(t), t, u) for t, u in found]
    candidates = [c for c in candidates if c[0] >= MIN_SCORE]
    return max(candidates, key=lambda c: c[0]) if candidates else None


def best_lines(found):
    candidates = [(lines_score(t), t, u) for t, u in found]
    candidates = [c for c in candidates if c[0] > 0]
    return max(candidates, key=lambda c: c[0]) if candidates else None


def update_deal(entry, best):
    new_score, text, url = best
    if text == entry["deal"] and url == entry["url"]:
        return False
    # Only replace a deal when the new text is clearly better, so two similar candidates can't flip-flop forever.
    if text != entry["deal"] and new_score < score(entry["deal"]) + MARGIN:
        print(f"  keeping old deal (new text not clearly better): {text}")
        return False
    print(f"  new: {text}")
    entry["deal"], entry["url"] = text, url
    entry["freePhone"] = offers_phone(text)
    entry["updated"] = date.today().isoformat()
    if entry.get("plans") and not plans_still_apply(entry, text):
        print(f"  plans scope {entry['plans']} no longer in the text - dropped")
        del entry["plans"]
    return True


def update_lines(entry, best):
    """Store the best multi-line price sentence as a note (shown on cards; never used to compute prices)."""
    new_score, text, _ = best
    old = entry.get("lines", "")
    if text == old or (old and new_score < lines_score(old) + MARGIN):
        return False
    print(f"  new multi-line note: {text}")
    entry["lines"] = text
    entry["linesUpdated"] = date.today().isoformat()
    return True


def main():
    with open(PATH) as f:
        data = json.load(f)
    changed = False
    for entry in data["deals"]:
        print(entry["carrier"])
        found = gather(entry)
        time.sleep(3)
        best = best_candidate(found)
        if not best:
            print("  keeping old deal")
        else:
            changed |= update_deal(entry, best)
        lines = best_lines(found)
        if lines:
            changed |= update_lines(entry, lines)
        else:
            print("  no multi-line price found")
        for kind in EXTRAS:
            extra = best_extra(kind, found)
            if extra:
                changed |= update_extra(entry, kind, extra)
    if changed:
        data["updated"] = date.today().isoformat()
        with open(PATH, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("deals.json updated")
    else:
        print("no change")


if __name__ == "__main__":
    main()
