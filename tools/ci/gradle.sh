#!/usr/bin/env bash
# Запуск Gradle в CI: при ошибке ключевые строки лога публикуются как аннотации ::error::,
# чтобы причину было видно в сводке запуска без скачивания полного лога.
set -uo pipefail
LOG="${RUNNER_TEMP:-/tmp}/gradle-$(date +%s%N).log"
./gradlew --stacktrace "$@" 2>&1 | tee "$LOG"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  enc() { python3 -c 'import sys; s=sys.stdin.read(); print(s.replace("%","%25").replace("\r","%0D").replace("\n","%0A"), end="")'; }
  # 1) Раздел «What went wrong» (до «Try:»)
  awk '/\* What went wrong:/{f=1} /\* Try:/{f=0} f' "$LOG" | head -c 6000 | { msg=$(enc); [ -n "$msg" ] && echo "::error title=Gradle: what went wrong::$msg"; }
  # 2) Ошибки компилятора Kotlin/AAPT/lint
  grep -E '^e: |error: |AAPT: error|Could not resolve|Could not find|Unresolved reference|Lint found' "$LOG" | sort -u | head -40 | head -c 6000 | { msg=$(enc); [ -n "$msg" ] && echo "::error title=Compiler/resolve errors::$msg"; }
  # 3) Причины исключения
  grep -E '^Caused by: ' "$LOG" | sort -u | head -15 | head -c 4000 | { msg=$(enc); [ -n "$msg" ] && echo "::error title=Caused by::$msg"; }
  # 4) Упавшие тесты — с текстом ошибки (несколько строк после «FAILED»)
  grep -E 'FAILED$|> .* FAILED' "$LOG" | head -30 | { msg=$(enc); [ -n "$msg" ] && echo "::error title=Failed tests::$msg"; }
  grep -A10 -E '^[A-Za-z0-9_.]+Test > .* FAILED$' "$LOG" | grep -vE '^\s+at (org\.junit|java\.|jdk\.|org\.gradle|kotlin\.coroutines|kotlinx\.coroutines\.test)' | head -60 | head -c 6000 | { msg=$(enc); [ -n "$msg" ] && echo "::error title=Test failures (details)::$msg"; }
fi
exit "$rc"
