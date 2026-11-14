#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLIN_BIN="$(command -v kotlinc)"
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$KOTLIN_BIN")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
test -f "$CP"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/iumrah/beta
kotlinc -cp "$CP" scripts/qa/Stage10SerializationStubs.kt scripts/qa/ArchitectureApiStub.kt scripts/qa/ArchitectureParityRegression.kt \
  "$S/data/nuitee/NuiteeDailyCachePolicy.kt" "$S/data/flight/FlightOperationalPolicy.kt" "$S/data/flight/IumrahFlightDetailService.kt" -d "$TMP/qa.jar"
kotlin -cp "$CP:$TMP/qa.jar" com.iumrah.beta.data.flight.ArchitectureParityRegressionKt
