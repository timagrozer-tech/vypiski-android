#!/usr/bin/env bash
# Выписки — all automated checks. Builds NOTHING (no APK/EXE): unpacks the newest project ZIP, generates synthetic
# test data (invented names) and runs unit + browser tests against the page in headless Chromium.
# Needs: node ≥ 18, python3 with playwright (+ `python3 -m playwright install chromium`), python-docx, openpyxl, Pillow.
# Optional: VYP_ELECTRON_DIR=<unpacked Electron runtime> + xvfb-run — then the Windows shell is also checked (nothing is packaged).
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
run "Вход, блокировка, роли (12.77)"        python3 "$HERE/e2e/auth_roles.py" "$PAGE"
run "Поиск «Люди и события»"                python3 "$HERE/e2e/search_people_events.py" "$PAGE"
run "Массовое создание и центр печати"      python3 "$HERE/e2e/batch_and_print.py" "$PAGE"
run "Excel-аналитика (отчёты)"              python3 "$HERE/e2e/excel_reports.py" "$PAGE"
run "Excel Pro: импорт и сверка"            python3 "$HERE/e2e/excel_pro.py" "$PAGE"
run "Зашифрованные резервные копии"         python3 "$HERE/e2e/encrypted_backup.py" "$PAGE"
run "Согласование, выдача, очередь, Ctrl+K" python3 "$HERE/e2e/workflow.py" "$PAGE"
run "Раздел «Массовое создание», несколько лет в поиске" python3 "$HERE/e2e/bulk_create.py" "$PAGE"
run "Разбор после загрузки, поиск человека без таблицы, замер" python3 "$HERE/e2e/preparse.py" "$PAGE"
run "Массовое создание: наборы и «Ещё»" python3 "$HERE/e2e/bulk_sets.py" "$PAGE"
run "Массовое создание: продолжение после перерыва" python3 "$HERE/e2e/bulk_resume.py" "$PAGE"
run "Отчёты о сбоях" python3 "$HERE/e2e/crash_reports.py" "$PAGE"
run "XLSX и CSV: дословный текст без хранения в памяти" python3 "$HERE/e2e/xlsx_text.py" "$PAGE"
run "Нехватка места: понятное сообщение" python3 "$HERE/e2e/storage_full.py" "$PAGE"
run "Все кнопки всех экранов без ошибок (телефон)" python3 "$HERE/e2e/all_buttons.py" "$PAGE" 390
run "Все кнопки всех экранов без ошибок (ПК)" python3 "$HERE/e2e/all_buttons.py" "$PAGE" 1366
run "Поиск именно этого человека"         python3 "$HERE/e2e/person_exact.py" "$PAGE"
run "Нет фоновой нагрузки на экранах"     python3 "$HERE/e2e/idle_load.py" "$PAGE" "$WORK/profile"
run "Живые папки Windows (без Electron)"    node "$HERE/unit/sources.test.cjs"
if [ -n "${VYP_ELECTRON_DIR:-}" ]; then
  D="$WORK/desktop"; cp -r "$HERE/../desktop" "$D"; mkdir -p "$D/app"; cp "$PAGE/index.html" "$D/app/index.html"
  VYP_DESKTOP="$D" run "Windows-оболочка: папки, печать, PDF" python3 "$HERE/e2e/desktop_live_folders.py"
fi
echo; echo "Готово. Отчёты и скриншоты: $WORK/out"
