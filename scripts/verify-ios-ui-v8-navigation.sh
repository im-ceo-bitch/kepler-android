#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/iumrah/beta
kotlinc -cp "$CP" scripts/qa/v7-store/Flow.kt scripts/qa/Stage10SerializationStubs.kt scripts/qa/DiscoveryNavigationRegression.kt \
 "$S/core/navigation/AppChromeStore.kt" "$S/domain/trip/TripModels.kt" "$S/models/flight/AirportModels.kt" "$S/models/flight/FlightSearchModels.kt" -d "$TMP/nav.jar"
kotlin -cp "$CP:$TMP/nav.jar" com.iumrah.beta.core.navigation.DiscoveryNavigationRegressionKt
