#!/usr/bin/env bash
# JVM type/behavior check of actual Smart service, JourneyStore and models with mock IO boundaries.
set -euo pipefail
cd "$(dirname "$0")/.."
S=app/src/main/java/com/iumrah/beta
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
kotlinc -nowarn -cp "$CP" scripts/qa/sync02/*.kt scripts/qa/v7-store/Flow.kt \
  "$S"/domain/trip/*.kt "$S"/domain/nuitee/*.kt "$S"/models/flight/*.kt "$S"/models/hotel/*.kt "$S"/models/nuitee/*.kt \
  "$S/domain/pricing/PricingModels.kt" "$S/domain/journey/JourneyStore.kt" "$S/domain/smart/SmartPreferences.kt" \
  "$S/data/flight/AviasalesFlightDiscoveryService.kt" "$S/data/flight/FlightDiscoveryVerifiedSegment.kt" "$S/data/flight/FlightReferenceCatalog.kt" \
  "$S/data/smart/SmartConfiguratorService.kt" "$S/data/smart/HomePublicContentCache.kt" -d "$TMP/smart.jar"
kotlin -cp "$CP:$TMP/smart.jar" SmartTestKt
