#!/usr/bin/env bash
# Выписки — all automated checks. Builds NOTHING (no APK/EXE): unpacks the newest project ZIP, generates synthetic
# test data (invented names) and runs unit + browser tests against the page in headless Chromium.
# Needs: node ≥ 18, python3 with playwright (+ `python3 -m playwright install chromium`), python-docx, Pillow.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ZIP=$(ls -1 "$HERE"/../Vypiski-Android-Stage-*-project.zip | sort -V | tail -n 1)
WORK=$(mktemp -d)
echo "Project: $(basename "$ZIP")   work dir: $WORK"
unzip -q "$ZIP" -d "$WORK/project"
PAGE="$WORK/project/app/src/main/assets"
MANY="${MANY:-1500}" python3 "$HERE/fixtures/make_fixtures.py" "$WORK/fx" >/dev/null
export VYP_FIX="$WORK/fx" VYP_OUT="$WORK/out"
run(){ echo; echo "== $1"; shift; "$@"; }
run "Разбор ФИО и событий (unit)"            node "$HERE/unit/register.test.cjs" "$PAGE/index.html"
run "DOCX: фоновый разбор = DOMParser"      python3 "$HERE/e2e/docx_equivalence.py" "$PAGE"
run "Приёмка DESIGN_SPEC 2.0"               python3 "$HERE/e2e/acceptance.py" "$PAGE"
run "Мастер выписки"                         python3 "$HERE/e2e/wizard_regression.py" "$PAGE" "$WORK/out/wizard"
run "Фоновый импорт и продолжение"          python3 "$HERE/e2e/background_import.py" "$PAGE"
run "Имитация приложения Android"           python3 "$HERE/e2e/android_simulation.py" "$PAGE"
run "Тёмная тема — телефон"                 python3 "$HERE/e2e/dark_theme_scan.py" "$PAGE" 390
run "Тёмная тема — Windows"                 python3 "$HERE/e2e/dark_theme_scan.py" "$PAGE" 1366
echo; echo "Готово. Отчёты и скриншоты: $WORK/out"
