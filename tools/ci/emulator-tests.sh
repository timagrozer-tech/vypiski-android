#!/usr/bin/env bash
# Инструментальные тесты на эмуляторе (сеть отключена) + вывод отчётов тестов в сводку запуска.
set -uo pipefail
adb shell svc wifi disable || true
adb shell svc data disable || true
adb logcat -c || true
tools/ci/gradle.sh connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
# 1) Отчёт детектора из logcat (тег DetectorAccuracy)
rep=$(adb logcat -d -s DetectorAccuracy:I 2>/dev/null | grep -v '^-----' | sed 's/^.*DetectorAccuracy: //' | tail -1 | tr -d '\r')
[ -n "$rep" ] && echo "::notice title=Точность поиска документа (эмулятор, синтетический набор)::$rep"
# 2) Тексты провалов из XML-результатов (на случай, если logcat пуст)
for x in $(find . -path '*androidTest-results/connected*' -name '*.xml' 2>/dev/null); do
  python3 - "$x" <<'PY'
import sys, xml.etree.ElementTree as ET
t = ET.parse(sys.argv[1])
for tc in t.iter('testcase'):
    for f in tc.findall('failure'):
        msg = (f.get('message') or f.text or '').strip().splitlines()
        text = ' '.join(msg[:3])[:3000].replace('%', '%25')
        print(f"::error title=Провал {tc.get('classname','').split('.')[-1]}.{tc.get('name')}::{text}")
PY
done
exit $rc
