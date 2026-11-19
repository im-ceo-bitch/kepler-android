#!/usr/bin/env bash
set -euo pipefail

test -f scripts/verify-stage4.sh || { echo 'STOP: Stage 005 requires Stage 004 verifier.'; exit 2; }
bash scripts/verify-stage4.sh

required=(
  ANDROID_STAGE_005_TRIP_FLIGHTS_APPLIED.txt
  app/src/main/java/com/iumrah/beta/domain/journey/JourneyStore.kt
  app/src/main/java/com/iumrah/beta/models/flight/AirportModels.kt
  app/src/main/java/com/iumrah/beta/models/flight/FlightModels.kt
  app/src/main/java/com/iumrah/beta/models/flight/FlightSearchModels.kt
  app/src/main/java/com/iumrah/beta/data/flight/AirportSearchService.kt
  app/src/main/java/com/iumrah/beta/data/flight/FlightFareCalendarService.kt
  app/src/main/java/com/iumrah/beta/data/flight/IgnavFlightInventoryProvider.kt
  app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt
  app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt
  app/src/main/java/com/iumrah/beta/ui/flights/FlightSearchScreen.kt
)
for f in "${required[@]}"; do test -s "$f" || { echo "STOP: missing Stage 005 file: $f"; exit 1; }; done

grep -q '/api/package/flights/search' app/src/main/java/com/iumrah/beta/data/flight/IgnavFlightInventoryProvider.kt
grep -q 'fareScope = FlightFareScope.TOTAL_PARTY' app/src/main/java/com/iumrah/beta/data/flight/IgnavFlightInventoryProvider.kt
grep -q 'itinerary.fareScope != "total_party"' app/src/main/java/com/iumrah/beta/data/flight/IgnavFlightInventoryProvider.kt
grep -q 'AppRoute.TripBuilder -> TripBuilderScreen' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt
grep -Eq 'AppRoute.HotelSelection -> (com\.iumrah\.beta\.ui\.trip\.)?UnifiedHotelEntry\(' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt || { echo 'STOP: HotelSelection must use the unified hotel entry'; exit 1; }
grep -q 'fun UnifiedHotelEntry' app/src/main/java/com/iumrah/beta/ui/trip/UnifiedHotelEntry.kt
grep -q 'AppRoute.Flights -> FlightSearchScreen' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt
if grep -q 'AppRoute.TripBuilder -> StagePlaceholderScreen' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt; then
  echo 'STOP: TripBuilder is still a placeholder.'; exit 1
fi
if grep -q 'AppRoute.Flights -> StagePlaceholderScreen' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt; then
  echo 'STOP: Flights is still a placeholder.'; exit 1
fi
grep -q 'BigDecimal("0.50")' app/src/main/java/com/iumrah/beta/domain/pricing/LocalPackagePricingEngine.kt

echo 'Stage 005 trip/flight structural checks passed.'
