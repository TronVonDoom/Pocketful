#!/usr/bin/env python3
"""
Builds the local card catalog from TCGdex.

Why this exists: the app currently asks the network every question. A set has not
changed since the day it was printed, so re-fetching Base Set is paying a round trip
for an answer that was already true in 1999. This pulls each set once and writes it
to disk in the shape the app reads.

Two files come out per set, and the split is the whole point:

  sets/<id>.json    Static. Name, number, rarity, art stem, attacks. Immutable once
                    a set is released, so it ships with the app and never expires.
  prices/<id>.json  Volatile. Market prices, stamped with the hour they were taken.
                    Never bundled -- it would be stale before the APK finished
                    uploading. Refreshed live, on a TTL.

Mixing the two into one document is the mistake this layout is built to avoid: it
would make the immutable half expire at the speed of the volatile half.

Usage:
    python pull_catalog.py --sets base1 base2 base3      # named sets
    python pull_catalog.py --first 3                     # first N by release date
    python pull_catalog.py --all                         # the entire catalog
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests

BASE = "https://api.tcgdex.net/v2"
LANG = "en"

# Six at a time, matching the ceiling CatalogSync already uses against this host.
# Unbounded is a rate limit; serial is an afternoon.
WORKERS = 6

OUT = Path(__file__).resolve().parent.parent.parent / "catalog"

# Fields that describe the card as printed. Everything here is fixed at print time.
STATIC_FIELDS = (
    "id", "localId", "name", "rarity", "illustrator", "category", "image",
    "hp", "types", "stage", "evolveFrom", "description", "retreat", "suffix",
    "regulationMark", "dexId", "variants", "attacks", "abilities",
    "weaknesses", "resistances", "trainerType", "energyType", "effect", "level",
)

SET_INDEX_QUERY = """
{
  sets {
    id name logo symbol releaseDate
    cardCount { official total }
    serie { id name }
  }
}
"""


def session() -> requests.Session:
    s = requests.Session()
    s.headers["User-Agent"] = "Pocketful-catalog-builder/0.1 (personal collection app)"
    return s


def get_json(s: requests.Session, url: str, tries: int = 4):
    """GET with backoff. The API answers 503 under load often enough to matter."""
    for attempt in range(tries):
        try:
            r = s.get(url, timeout=30)
            if r.status_code == 200:
                return r.json()
            if r.status_code == 404:
                return None
        except requests.RequestException:
            pass
        time.sleep(1.5 * (attempt + 1))
    return None


def set_index(s: requests.Session) -> list:
    """Every set, dated and filed under its era. Only GraphQL carries both fields."""
    for attempt in range(4):
        try:
            r = s.post(f"{BASE}/graphql", json={"query": SET_INDEX_QUERY}, timeout=30)
            if r.status_code == 200:
                sets = (r.json().get("data") or {}).get("sets")
                if sets:
                    return sets
        except requests.RequestException:
            pass
        time.sleep(1.5 * (attempt + 1))
    raise SystemExit("Could not reach the set index.")


def split_card(card: dict):
    """One upstream card document into its immutable half and its volatile half."""
    static = {k: card[k] for k in STATIC_FIELDS if card.get(k) is not None}

    pricing = card.get("pricing") or {}
    tcg = pricing.get("tcgplayer") or {}
    prices = {}
    for finish, quote in tcg.items():
        # 'unit' and 'updated' sit alongside the finishes rather than inside them.
        if not isinstance(quote, dict):
            continue
        market = quote.get("marketPrice")
        if market:
            prices[finish] = round(market * 100)  # cents, matching domain.Money
    return static, ({"id": card["id"], "usd_cents": prices} if prices else None)


def pull_set(s: requests.Session, set_id: str):
    detail = get_json(s, f"{BASE}/{LANG}/sets/{set_id}")
    if not detail:
        print(f"  !! {set_id}: set document unavailable", file=sys.stderr)
        return None

    briefs = detail.get("cards") or []
    cards = []
    prices = []

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        full = list(pool.map(lambda b: get_json(s, f"{BASE}/{LANG}/cards/{b['id']}"), briefs))

    missing = []
    for brief, card in zip(briefs, full):
        if not card:
            missing.append(brief["id"])
            continue
        static, price = split_card(card)
        cards.append(static)
        if price:
            prices.append(price)

    if missing:
        print(f"  !! {set_id}: {len(missing)} cards failed: {missing[:5]}", file=sys.stderr)

    # Sorted by printed number so the file reads like the set does. localId is not
    # always numeric (promos, "TG01", "SV049"), so sort numerically where possible
    # and lexically otherwise rather than crashing on the exceptions.
    def order(c):
        raw = str(c.get("localId", ""))
        digits = "".join(ch for ch in raw if ch.isdigit())
        return (0, int(digits), raw) if digits else (1, 0, raw)

    cards.sort(key=order)

    set_doc = {
        "id": detail["id"],
        "name": detail["name"],
        "serie": detail.get("serie"),
        "releaseDate": detail.get("releaseDate"),
        "cardCount": detail.get("cardCount"),
        "logo": detail.get("logo"),
        "symbol": detail.get("symbol"),
        "abbreviation": detail.get("abbreviation"),
        "legal": detail.get("legal"),
        "cards": cards,
    }
    price_doc = {
        "setId": detail["id"],
        "source": "tcgplayer",
        "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "cards": prices,
    }
    return set_doc, price_doc


def main() -> None:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--sets", nargs="+", help="explicit set ids")
    g.add_argument("--first", type=int, help="first N sets by release date")
    g.add_argument("--all", action="store_true")
    args = ap.parse_args()

    s = session()
    index = set_index(s)
    dated = sorted(
        (x for x in index if x.get("releaseDate")),
        key=lambda x: (x["releaseDate"], x["name"]),
    )

    if args.sets:
        wanted = args.sets
    elif args.first:
        wanted = [x["id"] for x in dated[: args.first]]
    else:
        wanted = [x["id"] for x in dated]

    (OUT / "sets").mkdir(parents=True, exist_ok=True)
    (OUT / "prices").mkdir(parents=True, exist_ok=True)

    # The index itself is worth writing: it is what the browse screen opens on, and
    # it is the one document that legitimately changes when a new set is announced.
    stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    (OUT / "index.json").write_text(
        json.dumps({"fetchedAt": stamp, "sets": dated}, indent=1, ensure_ascii=False),
        encoding="utf-8",
    )

    for n, set_id in enumerate(wanted, 1):
        print(f"[{n}/{len(wanted)}] {set_id} ...", flush=True)
        result = pull_set(s, set_id)
        if not result:
            continue
        set_doc, price_doc = result
        (OUT / "sets" / f"{set_id}.json").write_text(
            json.dumps(set_doc, indent=1, ensure_ascii=False), encoding="utf-8")
        (OUT / "prices" / f"{set_id}.json").write_text(
            json.dumps(price_doc, indent=1, ensure_ascii=False), encoding="utf-8")
        print(f"      {len(set_doc['cards'])} cards, {len(price_doc['cards'])} priced")


if __name__ == "__main__":
    main()
