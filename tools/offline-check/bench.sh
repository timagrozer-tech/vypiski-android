#!/bin/bash
# Prints synthetic quality/speed numbers for BurstDenoiser (JVM, not a device measurement). No APK.
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
KOTLIN_LIB=${KOTLIN_LIB:-/opt/gradle/lib}
V=$(ls "$KOTLIN_LIB" | sed -n 's/^kotlin-compiler-embeddable-\(.*\)\.jar$/\1/p' | head -1)
STD=$KOTLIN_LIB/kotlin-stdlib-$V.jar
KCP=$KOTLIN_LIB/kotlin-compiler-embeddable-$V.jar:$STD:$KOTLIN_LIB/kotlin-reflect-$V.jar:$KOTLIN_LIB/kotlin-script-runtime-$V.jar:$KOTLIN_LIB/kotlin-daemon-embeddable-$V.jar:$(ls $KOTLIN_LIB/trove4j-*.jar | head -1):$(ls $KOTLIN_LIB/annotations-*.jar | head -1):$(ls $KOTLIN_LIB/kotlinx-coroutines-core-jvm-*.jar | head -1)
SRC=${1:-$ROOT/app/src/main/java/ru/revvl/cameralab/BurstDenoiser.kt}
B=$(mktemp -d); trap 'rm -rf "$B"' EXIT
java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD" -d "$B" "$SRC" "$HERE/BurstBench.kt" 2>&1 | grep -v "^Picked up" | grep error
java -Xmx2g -cp "$B:$STD" BurstBenchKt 2>&1 | grep -v "^Picked up"
