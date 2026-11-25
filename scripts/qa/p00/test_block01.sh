#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
COROUTINES="$(dirname "$(command -v kotlinc)")/../lib/kotlinx-coroutines-core-jvm.jar"
K="$ROOT/app/src/main/java/com/iumrah/beta"
kotlinc -cp "$COROUTINES" "$K/core/navigation/AppChromeStore.kt" "$K/core/navigation/RouteStateKeys.kt" "$K/core/design/IOSParityLayout.kt" \
 "$ROOT/scripts/qa/p00/TripStubs.kt" "$ROOT/scripts/qa/p00/FlowShim.kt" "$ROOT/scripts/qa/p00/JourneyStub.kt" "$ROOT/scripts/qa/p00/SmartDraftStub.kt" "$ROOT/scripts/qa/p00/Block01LayoutStateSmoke.kt" -include-runtime -d "$OUT/check.jar"
java -cp "$OUT/check.jar:$COROUTINES" com.iumrah.beta.core.navigation.Block01LayoutStateSmokeKt
