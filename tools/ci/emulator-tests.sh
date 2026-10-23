#!/usr/bin/env bash
# Инструментальные тесты на эмуляторе (сеть отключена) + вывод отчётов тестов в сводку запуска.
set -uo pipefail
adb shell svc wifi disable || true
adb shell svc data disable || true
tools/ci/gradle.sh connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
for pkg in ru.scanner291.core.imageprocessing.test; do
  f=$(adb shell cat /sdcard/Android/data/$pkg/files/detector-report.txt 2>/dev/null | tr -d '\r')
  [ -n "$f" ] && echo "::notice title=Точность поиска документа (эмулятор, синтетический набор)::$f"
done
exit $rc
