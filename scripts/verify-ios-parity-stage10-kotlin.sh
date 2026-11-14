#!/usr/bin/env bash
# Real production domain/rules files are compiled to JVM and exercised without Android SDK.
# This is NOT an APK/Compose compilation. CI Gradle still required for final release acceptance.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
command -v kotlinc >/dev/null || { echo 'Kotlin CLI missing; run Android CI for compilation.' >&2; exit 2; }
command -v kotlin >/dev/null || { echo 'Kotlin runtime missing.' >&2; exit 2; }
SRC="app/src/main/java/com/iumrah/beta"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc \
  scripts/qa/Stage10SerializationStubs.kt \
  scripts/qa/Stage10FlightOfferStub.kt \
  scripts/qa/Stage10JvmRegression.kt \
  "$SRC/models/flight/AirportModels.kt" \
  "$SRC/models/flight/FlightSearchModels.kt" \
  "$SRC/models/flight/CuratedFlightRecommendationModels.kt" \
  "$SRC/domain/trip/TripModels.kt" \
  "$SRC/domain/trip/TripStayPlanner.kt" \
  "$SRC/domain/trip/TransferModels.kt" \
  "$SRC/models/nuitee/NuiteeModels.kt" \
  "$SRC/domain/nuitee/NuiteeRules.kt" \
  "$SRC/domain/nuitee/NuiteeJourneyPricing.kt" \
  "$SRC/ui/flights/GuidedFlightSelectionEngine.kt" \
  "$SRC/domain/umrahflow/UmrahFlowProgress.kt" \
  "$SRC/core/config/AppConfig.kt" \
  "$SRC/domain/umrahflow/UmrahFlowAudioUrlPolicy.kt" \
  -d "$TMP/qa.jar"
kotlin -cp "$TMP/qa.jar" com.iumrah.beta.ui.flights.Stage10JvmRegression
