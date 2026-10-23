#!/usr/bin/env bash
# Сборка офлайн-пакета «Высокая точность» (ADR-0016): scanner291-ocr-best.zip с rus/eng из tessdata_best.
# Суммы сверяются с закреплёнными в приложении (core/ocr/src/main/assets/tessdata_best.sha256) — приложение
# примет только эти файлы. Запуск: tools/ocr-lab/make_best_pack.sh [папка-результата]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/build/ocr-pack}"; mkdir -p "$OUT"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
git clone -q --filter=blob:none --no-checkout --depth 1 https://github.com/tesseract-ocr/tessdata_best.git "$TMP/tb"
git -C "$TMP/tb" sparse-checkout set --no-cone /rus.traineddata /eng.traineddata /LICENSE
git -C "$TMP/tb" checkout -q
( cd "$TMP/tb" && grep -v '^#' "$ROOT/core/ocr/src/main/assets/tessdata_best.sha256" | sha256sum -c - )
( cd "$TMP/tb" && sha256sum rus.traineddata eng.traineddata > SHA256SUMS.txt && zip -q -X "$OUT/scanner291-ocr-best.zip" rus.traineddata eng.traineddata LICENSE SHA256SUMS.txt )
sha256sum "$OUT/scanner291-ocr-best.zip"
