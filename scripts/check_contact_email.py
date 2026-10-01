#!/usr/bin/env python3
"""Fails if any tracked source file uses an email address other than the one official contact address.

Nirog Bhumi has one public contact address (support, privacy requests, deletion requests, Grievance Officer).
Test fixtures, e2e seed data and cloud service-account identifiers are not contact addresses and are skipped.
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OFFICIAL = "priyanshu@nirogbhumi.com"
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")
SKIP_PATH = re.compile(r"(^|/)(e2e|test|tests|rules-tests|node_modules|dist|build|lib)/|package-lock\.json$|\.(png|jpg|webp|jar|ttf|otf|ico|keystore|jks)$")
SKIP_MATCH = re.compile(r"\.iam\.gserviceaccount\.com$|\.example$|@example\.(com|org)$|^nirogbhumi\.com@evil")

files = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True).splitlines()
problems = []
for rel in files:
    if SKIP_PATH.search(rel):
        continue
    try:
        text = open(os.path.join(ROOT, rel), encoding="utf-8").read()
    except (UnicodeDecodeError, FileNotFoundError, IsADirectoryError):
        continue
    for n, line in enumerate(text.splitlines(), 1):
        for m in EMAIL.findall(line):
            if m.lower() == OFFICIAL or SKIP_MATCH.search(m):
                continue
            # URLs such as https://user@host/ in docs/tests describing blocked links are not addresses.
            if re.search(r"https?://[^\s]*" + re.escape(m), line):
                continue
            problems.append(f"{rel}:{n}: {m}")

if problems:
    print(f"Only {OFFICIAL} may be used as a contact address. Found:")
    print("\n".join(problems))
    sys.exit(1)
print(f"ok: no contact address other than {OFFICIAL}")
