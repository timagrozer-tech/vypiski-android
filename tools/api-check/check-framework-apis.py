#!/usr/bin/env python3
"""Checks every Android framework API the app calls (required-framework-apis.txt) against the
official AOSP signature files. Read-only; no SDK, no build, no APK.

Usage: check-framework-apis.py <frameworks_base/core/api/current.txt> <mediaprovider/apex/framework/api/current.txt>
Get the files (small sparse clones of the official GitHub mirrors, branch android16-release = API 36):
  git clone --depth 1 --branch android16-release --filter=blob:none --sparse https://github.com/aosp-mirror/platform_frameworks_base fwb
  git -C fwb sparse-checkout set --no-cone '/core/api/current.txt'
  git clone --depth 1 --branch android16-release --filter=blob:none --sparse https://github.com/aosp-mirror/platform_packages_providers_mediaprovider mp
  git -C mp sparse-checkout set --no-cone '/apex/framework/api/current.txt'
"""
import os, re, sys

def norm(text):
    text = re.sub(r'@[A-Za-z0-9_.]+(\([^)]*\))? ', '', text).replace('!', '')
    prev = None
    while prev != text:  # drop parameter names: "Type name," -> "Type,"
        prev = text
        text = re.sub(r'([][A-Za-z0-9_.<>?]+(\.\.\.)?) [a-z][A-Za-z0-9_]*([,)])', r'\1\3', text)
    return re.sub(' +', ' ', text)

def main():
    if len(sys.argv) != 3:
        print(__doc__); return 2
    sources = {"framework": norm(open(sys.argv[1]).read()), "mediaprovider": norm(open(sys.argv[2]).read())}
    here = os.path.dirname(os.path.abspath(__file__))
    ok = miss = 0
    for line in open(os.path.join(here, "required-framework-apis.txt"), encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        module, sig = line.split("|", 1) if "|" in line else ("framework", line)
        if norm(sig) in sources[module]:
            ok += 1
        else:
            miss += 1
            print(f"MISSING [{module}] {sig}")
    print(f"Android framework (API 36): {ok} found, {miss} missing")
    return 0 if miss == 0 else 1

if __name__ == "__main__":
    sys.exit(main())
