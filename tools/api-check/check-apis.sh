#!/bin/bash
# Checks that every AndroidX CameraX API the app calls exists in the public API signature files
# (camera/<module>/api/<version>.txt) of an androidx/androidx checkout. Read-only, no build, no APK.
# Usage: ./check-apis.sh /path/to/androidx-checkout [version, default 1.6.0-rc01]
# Get the files (small sparse clone):
#   git clone --depth 1 --filter=blob:none --sparse https://github.com/androidx/androidx ax
#   git -C ax sparse-checkout set --no-cone '/camera/*/api/*'
set -u
AX=${1:?path to androidx checkout}; VER=${2:-1.6.0-rc01}
HERE=$(cd "$(dirname "$0")" && pwd); TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
norm() { # drop annotations, "!" platform markers and parameter names
  sed -E 's/@[A-Za-z0-9_.]+(\([^)]*\))? //g; s/!//g' "$1" |
  sed -E ':a; s/([][A-Za-z0-9_.<>?]+(\.\.\.)?) [a-z][A-Za-z0-9_]*([,)])/\1\3/; ta' | tr -s ' '
}
miss=0; ok=0
while IFS='|' read -r module sig; do
  [[ -z "$module" || "$module" == \#* ]] && continue
  f="$AX/camera/$module/api/$VER.txt"
  [ -f "$f" ] || { echo "NO FILE $f"; miss=$((miss+1)); continue; }
  [ -f "$TMP/$module" ] || norm "$f" > "$TMP/$module"
  if grep -qF -- "$sig" "$TMP/$module"; then ok=$((ok+1)); else echo "MISSING [$module] $sig"; miss=$((miss+1)); fi
done < "$HERE/required-apis.txt"
echo "CameraX $VER: $ok found, $miss missing"
[ "$miss" -eq 0 ]
