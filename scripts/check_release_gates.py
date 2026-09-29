#!/usr/bin/env python3
"""Pre-upload release gates for Google Play.

Runs before the signed bundle is built (see .github/workflows/upload-google-play.yml).
Catches the things that get an app rejected or embarrass it in review:

  * legal pages still containing owner placeholders (only enforced with --strict,
    i.e. for closed/open/production tracks; the internal track may go out earlier),
  * "what's new" notes over Play's 500-character limit,
  * store graphics missing or the wrong size,
  * store-listing text over Play's limits.

Usage: python3 scripts/check_release_gates.py [--strict]
Exit code 0 = ok, 1 = a gate failed.
"""
import glob
import os
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
strict = "--strict" in sys.argv
problems = []


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as fh:
        return fh.read()


# 1. Legal placeholders -------------------------------------------------------
for page in sorted(glob.glob(os.path.join(ROOT, "console/public/legal/*.html"))):
    html = open(page, encoding="utf-8").read()
    found = re.findall(r'class="placeholder"[^>]*>([^<]*)<', html)
    if found and strict:
        problems.append(f"{os.path.relpath(page, ROOT)}: unresolved placeholder(s): {', '.join(found)}")
    elif found:
        print(f"note: {os.path.relpath(page, ROOT)} still has {len(found)} placeholder(s) (allowed for the internal track only)")

# 2. What's new -----------------------------------------------------------------
notes = sorted(glob.glob(os.path.join(ROOT, "play/whatsnew/whatsnew-*")))
if not notes:
    problems.append("play/whatsnew/: no release notes found")
for note in notes:
    text = open(note, encoding="utf-8").read().strip()
    if not text:
        problems.append(f"{os.path.relpath(note, ROOT)} is empty")
    elif len(text) > 500:
        problems.append(f"{os.path.relpath(note, ROOT)} is {len(text)} characters (Play limit 500)")


# 3. Graphics ---------------------------------------------------------------------
def png_size(path):
    with open(path, "rb") as fh:
        head = fh.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


for name, expected in (("icon-512.png", (512, 512)), ("feature-graphic.png", (1024, 500))):
    path = os.path.join(ROOT, "play/graphics", name)
    if not os.path.exists(path):
        problems.append(f"play/graphics/{name} is missing (run scripts/generate_play_graphics.mjs)")
        continue
    size = png_size(path)
    if size != expected:
        problems.append(f"play/graphics/{name} is {size}, expected {expected}")
    if os.path.getsize(path) > 1_000_000:
        problems.append(f"play/graphics/{name} is over 1 MB")

# 4. Listing text limits ---------------------------------------------------------------
listing = read("docs/PLAY_STORE_LISTING.md")


def section(title):
    match = re.search(rf"^## {re.escape(title)}\s*\n(.*?)(?=^## |\Z)", listing, re.S | re.M)
    return match.group(1).strip() if match else ""


for title, limit in (("Short description", 80), ("Hindi short description", 80), ("Full description", 4000), ("Hindi full description", 4000)):
    body = section(title)
    if not body:
        problems.append(f"docs/PLAY_STORE_LISTING.md: section '{title}' missing")
    elif len(body) > limit:
        problems.append(f"docs/PLAY_STORE_LISTING.md: '{title}' is {len(body)} characters (Play limit {limit})")

if problems:
    print("Release gates FAILED:")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print(f"Release gates passed{' (strict)' if strict else ''}.")
