#!/usr/bin/env bash
set -euo pipefail
bash scripts/verify-stage9.sh
required=(
  app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt
  app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt
  app/src/main/java/com/iumrah/beta/ui/trip/FlightDateCalendarDialog.kt
  app/src/main/java/com/iumrah/beta/ui/trip/ConfiguratorAirportSelector.kt
  app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt
  app/src/main/java/com/iumrah/beta/ui/flights/FlightSearchScreen.kt
  app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
  app/src/main/java/com/iumrah/beta/ui/packageflow/FinalPackageScreen.kt
  app/src/main/java/com/iumrah/beta/ui/booking/BookingCheckoutScreen.kt
  app/src/main/java/com/iumrah/beta/domain/trip/TransferModels.kt
  app/src/main/java/com/iumrah/beta/data/flight/CuratedFlightRecommendationService.kt
  app/src/main/java/com/iumrah/beta/models/flight/CuratedFlightRecommendationModels.kt
  app/src/main/res/drawable/centrum_air_logo.png
  app/src/main/res/drawable/iumrah_flights_calendar_logo.png
  app/src/main/res/drawable-nodpi/transfer_malibu.png
  app/src/main/res/drawable-nodpi/transfer_carnival.png
  app/src/main/res/drawable-nodpi/transfer_yukon.png
  app/src/main/res/drawable-nodpi/haramain_mark.png
  app/src/main/res/drawable-nodpi/haramain_gallery_train.png
  app/src/main/res/drawable-nodpi/haramain_gallery_station.jpg
  app/src/main/res/drawable-nodpi/haramain_gallery_interior.jpg
)
for f in "${required[@]}"; do test -s "$f" || { echo "STOP: missing Configurator parity file $f"; exit 1; }; done
grep -q 'data object ReturnFlights' app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt
grep -q 'data object TransferSelection' app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt
grep -q 'GeneratorStage.TRIP' app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt
grep -q 'GeneratorStage.HOTEL' app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt
grep -q 'GeneratorStage.FLIGHT' app/src/main/java/com/iumrah/beta/ui/flights/FlightSearchScreen.kt
grep -q 'GeneratorStage.TRANSFER' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
grep -q 'GeneratorStage.READY' app/src/main/java/com/iumrah/beta/ui/packageflow/FinalPackageScreen.kt
grep -q 'TransferVehicleKind.CARNIVAL' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
grep -q 'HaramainFareClass' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
grep -q 'packageEngine.packageQuote(state)' app/src/main/java/com/iumrah/beta/domain/pricing/PackageGenerator.kt
grep -q 'Random.nextInt(20, 41)' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
grep -q 'quoteProof' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt
grep -q 'hasFinalGeneratorQuote' app/src/main/java/com/iumrah/beta/domain/journey/JourneyStore.kt
grep -q 'packageMarkupRate: BigDecimal = BigDecimal("0.50")' app/src/main/java/com/iumrah/beta/domain/pricing/LocalPackagePricingEngine.kt
grep -q 'PUBLISHED_DIRECT' app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt || { echo 'STOP: iOS configurator requires Published Direct as a first-class path'; exit 1; }
grep -q 'FlightDateCalendarDialog' app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt || { echo 'STOP: Flexible Dates must use the iOS-style published-flight calendar'; exit 1; }
grep -q 'ConfiguratorAirportSelector' app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt || { echo 'STOP: TripBuilder must use the iOS-style airport selector + picker'; exit 1; }
if grep -q 'DatePickerDialog' app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt; then
  echo 'STOP: Android platform DatePicker is not iOS Configurator parity'; exit 1
fi
grep -q 'CuratedPublishedFlightSelection' app/src/main/java/com/iumrah/beta/ui/trip/FlightDateCalendarDialog.kt || { echo 'STOP: calendar must return published D1 flight IDs'; exit 1; }
grep -q 'API_BASE_URL = "https://iumrah.app"' app/src/main/java/com/iumrah/beta/core/config/AppConfig.kt || { echo 'STOP: Android must use the same iumrah.app backend'; exit 1; }
grep -q '/api/package/flights/recommendations' app/src/main/java/com/iumrah/beta/data/flight/CuratedFlightRecommendationService.kt || { echo 'STOP: Published Direct must read the Business/D1 recommendations projection'; exit 1; }
grep -q '/api/package/flights/recommendations/resolve' app/src/main/java/com/iumrah/beta/data/flight/CuratedFlightRecommendationService.kt || { echo 'STOP: Published Direct selection must resolve through the D1 resolver'; exit 1; }
grep -q 'refreshStorefrontPackages' app/src/main/java/com/iumrah/beta/data/hotel/HotelCatalogService.kt || { echo 'STOP: Hotel First storefront must support the server-owned refresh continuation'; exit 1; }
grep -q 'refreshStorefrontPackages' app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt || { echo 'STOP: Hotel First UI must continue incomplete server cache builds like iOS'; exit 1; }
if grep -qE 'HaramainFareClass\.BUSINESS|Business ·' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt; then
  echo 'STOP: current iOS Transfer flow exposes Standard Haramain only'; exit 1
fi
if grep -R -n '^import androidx.compose.foundation.layout.weight$' app/src/main/java/com/iumrah/beta; then
  echo 'STOP: explicit Compose weight import is incompatible with this project version'; exit 1
fi
# Material3 ModalBottomSheet is experimental in the Compose version used by this project.
while IFS= read -r f; do
  if ! grep -q 'ExperimentalMaterial3Api' "$f"; then
    echo "STOP: ModalBottomSheet requires ExperimentalMaterial3Api opt-in: $f"; exit 1
  fi
done < <(grep -R -lE 'ModalBottomSheet|rememberModalBottomSheetState' app/src/main/java/com/iumrah/beta --include='*.kt' || true)
if grep -R -nE 'material\.icons|Icons\.' \
  app/src/main/java/com/iumrah/beta/ui/generator \
  app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt \
  app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt \
  app/src/main/java/com/iumrah/beta/ui/flights/FlightSearchScreen.kt \
  app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt \
  app/src/main/java/com/iumrah/beta/ui/booking/BookingCheckoutScreen.kt; then
  echo 'STOP: Configurator flow must use Cupertino renderer, not Material icons'; exit 1
fi

# Home/storefront parity: the iOS dashboard renders real server-owned ready packages
# and the all-packages CTA opens the Hotels/Flights storefront, never the manual search.
grep -q 'storefrontFlightBoard(origin)' app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt || { echo 'STOP: Home ready packages must read the storefront flight board'; exit 1; }
grep -q 'storefrontPackages("flight-first", origin, 500)' app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt || { echo 'STOP: Home ready packages must read server-owned flight-first snapshots'; exit 1; }
grep -q 'openStorefrontFlights' app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt || { echo 'STOP: Ready packages CTA must open the Hotels/Flights storefront'; exit 1; }
grep -q 'data object StorefrontFlights' app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt || { echo 'STOP: storefront flights route missing'; exit 1; }
grep -q 'HotelsBoard.FLIGHTS' app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt || { echo 'STOP: storefront flights route must render the Flights board'; exit 1; }
grep -q 'listOf("Makkah", "Mecca", "Makka")' app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt || { echo 'STOP: Makkah alias catalogue parity missing'; exit 1; }

# Strict pixel/flow parity guards from the current SwiftUI Configurator.
grep -q 'val pagePadding = 18.dp' app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt || { echo 'STOP: Generator page padding must mirror iOS 18pt'; exit 1; }
grep -q 'val cardRadius = 28.dp' app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt || { echo 'STOP: Generator card radius must mirror iOS 28pt'; exit 1; }
grep -q 'val heroRadius = 34.dp' app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt || { echo 'STOP: Generator hero radius must mirror iOS 34pt'; exit 1; }
grep -q 'val controlHeight = 56.dp' app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt || { echo 'STOP: Generator control height must mirror iOS 56pt'; exit 1; }
grep -q 'detectHorizontalDragGestures' app/src/main/java/com/iumrah/beta/ui/generator/GeneratorChrome.kt || { echo 'STOP: Generator header must preserve iOS swipe carousel behavior'; exit 1; }
grep -q 'Посмотреть отель' app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt || { echo 'STOP: Primary Hotel View hotel action missing'; exit 1; }
grep -q 'Сменить отель' app/src/main/java/com/iumrah/beta/ui/trip/HotelSelectionScreen.kt || { echo 'STOP: Primary Hotel Change hotel action missing'; exit 1; }
grep -q 'height(340.dp)' app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt || { echo 'STOP: Transfer cinematic stage must mirror the iOS 340pt geometry'; exit 1; }
grep -q 'PackageTier.entries.map' app/src/main/java/com/iumrah/beta/ui/packageflow/FinalPackageScreen.kt || { echo 'STOP: Final Package must resolve all iOS package tiers'; exit 1; }
grep -q 'TierComparisonOption' app/src/main/java/com/iumrah/beta/ui/packageflow/FinalPackageScreen.kt || { echo 'STOP: Final Package tier carousel comparison is missing'; exit 1; }

# Global icon parity gate for all screens; evaluated by existing GitHub workflows.
bash scripts/verify-global-cupertino-icons.sh
echo 'Stage 010 iOS Configurator strict pixel/flow parity checks passed.'
# Compatibility bridge: older GitHub workflows in this repository still select
# the highest verifier only from Stage 1..10. When Stage 10 is invoked directly,
# delegate once to the current release verifier so CI cannot skip later stages.
if [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage25.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage25.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage24.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage24.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage23.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage23.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage22.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage22.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage21.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage21.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage20.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage20.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage19.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage19.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage18.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage18.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage17.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage17.sh
elif [[ "${IUMRAH_VERIFY_LATEST:-0}" != "1" && -s scripts/verify-stage16.sh ]]; then
  IUMRAH_VERIFY_LATEST=1 bash scripts/verify-stage16.sh
fi

