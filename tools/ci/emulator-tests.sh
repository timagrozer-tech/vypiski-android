#!/usr/bin/env bash
# Инструментальные тесты на эмуляторе (сеть отключена) + вывод отчётов тестов в сводку запуска.
set -uo pipefail
adb shell svc wifi disable || true
adb shell svc data disable || true
adb logcat -c || true
timeout 1800 tools/ci/gradle.sh connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
[ "$rc" = "124" ] && echo "::error title=Эмулятор::тесты не уложились в 30 минут (зависание)"
echo "gradle rc=$rc"
logs=$(adb logcat -d 2>/dev/null | grep 'DetectorAccuracy' | sed 's/^.*DetectorAccuracy[^:]*: //' | tr -d '\r' | tail -3 | tr '\n' ' ')
echo "logcat: $logs"
if [ -n "$logs" ]; then echo "::notice title=Точность поиска документа (эмулятор, синтетический набор)::$logs"; else echo "::notice title=Отчёт детектора::в logcat нет строк DetectorAccuracy"; fi
files=$(find . -path '*androidTest-results*' -name '*.xml' 2>/dev/null | tr '\n' ' ')
echo "::notice title=XML результатов инструментальных тестов::${files:-не найдено}"
for x in $files; do
  m=$(grep -o 'message="[^"]*"' "$x" | head -2 | cut -c1-2500 | tr '\n' ' ')
  [ -n "$m" ] && echo "::error title=Провал ($(basename "$x"))::$m"
  t=$(grep -A3 '<failure' "$x" | head -6 | cut -c1-400 | tr '\n' ' ')
  [ -n "$t" ] && echo "::error title=Стек провала ($(basename "$x"))::$t"
done
exit $rc
