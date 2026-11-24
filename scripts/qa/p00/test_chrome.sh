#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
COROUTINES="$(dirname "$(command -v kotlinc)")/../lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$COROUTINES" ]] || { echo 'Kotlin coroutines JAR unavailable'; exit 2; }
kotlinc -cp "$COROUTINES" \
  "$ROOT/app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt" \
  "$ROOT/scripts/qa/p00/TripStubs.kt" \
  "$ROOT/scripts/qa/p00/FlowShim.kt" \
  "$ROOT/scripts/qa/p00/JourneyStub.kt" \
  "$ROOT/scripts/qa/p00/ChromeStateSmoke.kt" \
  -include-runtime -d "$OUT/p00.jar"
java -cp "$OUT/p00.jar:$COROUTINES" com.iumrah.beta.core.navigation.ChromeStateSmokeKt
