#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/iumrah/beta
kotlinc "$D/ui/flights/GuidedFarePolicy.kt" "$D/ui/flights/GuidedFlightSelectionEngine.kt" "$D/data/flight/FlightDataParity.kt" "$D/data/flight/FlightReferenceCatalog.kt" "$D/data/flight/FlightDiscoveryVerifiedSegment.kt" "$D/data/flight/AviasalesFlightDiscoveryService.kt" "$D/models/flight/CuratedFlightRecommendationModels.kt" "$D/models/flight/FlightSearchModels.kt" scripts/qa/Stage10SerializationStubs.kt scripts/qa/block02/*.kt -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
