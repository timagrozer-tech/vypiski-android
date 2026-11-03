#!/bin/bash
# Offline source check WITHOUT Android SDK and WITHOUT building an APK.
#  1) pure Kotlin modules + unit tests compiled and run on the JVM (JUnit-compatible stubs);
#  2) all app Kotlin files type-checked against hand-written Java stubs of the Android/CameraX
#     API subset used by the app. Stubs reflect our reading of the API: a green result means the
#     modules are wired consistently, NOT that the real SDK accepts them. The real check is the
#     manual GitHub workflow "Verify sources (no APK)".
# Needs: java 17+, kotlin-compiler-embeddable (bundled with Gradle in $KOTLIN_LIB).
set -u
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
KOTLIN_LIB=${KOTLIN_LIB:-/opt/gradle/lib}
V=$(ls "$KOTLIN_LIB" | sed -n 's/^kotlin-compiler-embeddable-\(.*\)\.jar$/\1/p' | head -1)
[ -z "$V" ] && { echo "kotlin-compiler-embeddable not found in $KOTLIN_LIB"; exit 2; }
STD=$KOTLIN_LIB/kotlin-stdlib-$V.jar; REFL=$KOTLIN_LIB/kotlin-reflect-$V.jar
KCP=$KOTLIN_LIB/kotlin-compiler-embeddable-$V.jar:$STD:$REFL:$KOTLIN_LIB/kotlin-script-runtime-$V.jar:$KOTLIN_LIB/kotlin-daemon-embeddable-$V.jar:$(ls $KOTLIN_LIB/trove4j-*.jar | head -1):$(ls $KOTLIN_LIB/annotations-*.jar | head -1):$(ls $KOTLIN_LIB/kotlinx-coroutines-core-jvm-*.jar | head -1)
kc() { java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect "$@" 2>&1 | grep -v "^Picked up"; }
B=$(mktemp -d); trap 'rm -rf "$B"' EXIT
SRC=$ROOT/app/src/main/java/ru/revvl/cameralab; TST=$ROOT/app/src/test/java/ru/revvl/cameralab
PURE="BurstDenoiser CameraPolicy CameraText ManualExposure SessionPlanner CameraUiState UiModel ProControls Diagnostics"
FILES=""; for f in $PURE; do FILES="$FILES $SRC/$f.kt"; done
echo "== 1. Unit tests (pure modules)"
kc -cp "$STD:$REFL" -d "$B/t" "$HERE/junit-stub/org/junit/Junit.kt" "$HERE/junit-stub/org/junit/runner/RunWith.kt" "$HERE/Runner.kt" $FILES $TST/*.kt | grep -E "error" && exit 1
CLASSES=$(cd "$B/t" && find ru -name "*Test.class" | sed 's#/#.#g; s#\.class$##' | sort)
java -cp "$B/t:$STD:$REFL" RunnerKt $CLASSES 2>&1 | grep -v "^Picked up" | grep -vE "^ok " || exit 1
echo "== 2. Type-check of all app sources against API stubs"
python3 "$HERE/gen_android_stubs.py" "$B/jstub" >/dev/null
javac -nowarn -d "$B/jout" $(find "$B/jstub" -name "*.java") 2>&1 | grep -v "^Picked up"
OUT=$(kc -cp "$STD:$B/jout" -d "$B/k" "$HERE/kstub/EdgeToEdge.kt" $SRC/*.kt | grep -E "error|warning" | grep -v "RequiresOptIn")
if echo "$OUT" | grep -q "error"; then echo "$OUT"; exit 1; fi
[ -n "$OUT" ] && echo "$OUT"
echo "type-check: OK"
echo "== 3. Type-check of instrumented tests (androidTest) against the same stubs"
AT=$ROOT/app/src/androidTest/java/ru/revvl/cameralab
if [ -d "$AT" ]; then
  OUT=$(kc -cp "$STD:$B/jout:$B/k:$B/t" -d "$B/at" $AT/*.kt | grep -E "error|warning" | grep -v "RequiresOptIn")
  if echo "$OUT" | grep -q "error"; then echo "$OUT"; exit 1; fi
  [ -n "$OUT" ] && echo "$OUT"
  echo "androidTest type-check: OK (not run: needs a device/emulator)"
fi
