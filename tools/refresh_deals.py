#!/usr/bin/env python3
"""Refresh deals-data/deals.json using only free, keyless sources (stdlib only).

For each carrier already in the file: read the carrier's own deals page and a DuckDuckGo HTML search, keep the best
deal-sounding sentence that came from the carrier's OWN domain, and store it. Any failure keeps the old entry, so a
blocked request can never wipe the deals. freePhone/phones are never guessed - they stay as they are in the file.
Usage: python tools/refresh_deals.py [path/to/deals.json]
"""
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
MAX_LEN = 240


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9"})
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
    # Ignore legal/boilerplate and obvious non-offers.
    if any(b in low for b in ("cookie", "privacy", "terms", "sign in", "log in", "javascript")):
        return 0
    return n


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


def best_candidate(entry):
    base = entry["url"]
    candidates = []  # (score, text, url)
    try:
        for s in sentences(fetch(base)):
            s = tidy(s)
            candidates.append((score(s), s, base))
    except Exception as e:
        print(f"  page failed: {e}")
    time.sleep(2)
    try:
        for url, snippet in ddg_results(entry["carrier"] + " phone deals"):
            snippet = tidy(snippet)
            if url.startswith("https://") and same_site(url, base) and 25 <= len(snippet) <= MAX_LEN:
                candidates.append((score(snippet), snippet, url))
    except Exception as e:
        print(f"  search failed: {e}")
    candidates = [c for c in candidates if c[0] >= MIN_SCORE]
    return max(candidates, key=lambda c: c[0]) if candidates else None


def main():
    with open(PATH) as f:
        data = json.load(f)
    changed = False
    for entry in data["deals"]:
        print(entry["carrier"])
        best = best_candidate(entry)
        time.sleep(3)
        if not best:
            print("  keeping old entry")
            continue
        _, text, url = best
        if text != entry["deal"] or url != entry["url"]:
            print(f"  new: {text}")
            entry["deal"], entry["url"] = text, url
            changed = True
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
