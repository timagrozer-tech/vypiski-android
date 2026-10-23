#!/usr/bin/env bash
# Локальная компиляция и прогон тестов :core:domain без Gradle и без Android SDK.
# Использует Kotlin-компилятор, встроенный в дистрибутив Gradle (GRADLE_HOME/lib).
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
L="${GRADLE_LIB:-/opt/gradle/lib}"
KV=$(ls "$L" | sed -n 's/^kotlin-compiler-embeddable-\(.*\)\.jar$/\1/p' | head -1)
CP_COMPILER="$L/kotlin-compiler-embeddable-$KV.jar:$L/kotlin-stdlib-$KV.jar:$L/kotlin-script-runtime-$KV.jar:$L/kotlin-reflect-$KV.jar:$L/kotlin-daemon-embeddable-$KV.jar:$(ls $L/trove4j-*.jar):$(ls $L/annotations-*.jar | head -1):$(ls $L/kotlinx-coroutines-core-jvm-*.jar)"
STD="$L/kotlin-stdlib-$KV.jar"
OUT="${OUT:-$ROOT/build/localtest}"
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/test"
kc() { java -Dfile.encoding=UTF-8 -cp "$CP_COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"; }
echo "[1/3] Компиляция core/domain/src/main (Kotlin $KV)"
kc -cp "$STD" -d "$OUT/main" $(find "$ROOT/core/domain/src/main/kotlin" -name '*.kt')
echo "[2/3] Компиляция тестов"
kc -cp "$STD:$OUT/main" -d "$OUT/test" $(find "$ROOT/core/domain/src/test/kotlin" -name '*.kt') $(find "$ROOT/tools/localtest/src" -name '*.kt')
echo "[3/3] Прогон тестов"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$STD:$OUT/main:$OUT/test" LocalRunnerKt "$OUT/test"
