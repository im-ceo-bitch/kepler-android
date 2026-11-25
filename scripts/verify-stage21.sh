#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export IUMRAH_VERIFY_LATEST=1
bash scripts/verify-stage20.sh

THEME=app/src/main/java/com/iumrah/beta/core/design/IumrahTheme.kt
FLIGHTS=app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryPanel.kt
PAIR=app/src/main/java/com/iumrah/beta/ui/hotels/FlightFirstPackageDetailScreen.kt
HOTEL=app/src/main/java/com/iumrah/beta/ui/hotels/HotelDetailScreen.kt
HOTELS=app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt
AIRPORTS=app/src/main/java/com/iumrah/beta/ui/trip/ConfiguratorAirportSelector.kt
ZIYARATS=app/src/main/java/com/iumrah/beta/ui/ziyarats/ZiyaratJourneyScreen.kt

if grep -A2 -F 'private val IumrahTypography = Typography(' "$THEME" | grep -Fq 'fontFamily = IumrahFontFamily'; then
  echo 'STOP: unsupported Typography(fontFamily=...) compile regression'; exit 1
fi
# The legacy bottom-sheet was removed in Flights v47. Assert that the current
# full-screen detail is wired and that the old nullable returnAt expression
# cannot regress through a dormant duplicate composable.
if ! grep -Fq 'offer.returnAt?.let { returnAt ->' "$FLIGHTS"; then
  grep -Fq 'FlightDiscoveryDetailSheetV46(' "$FLIGHTS" || { echo 'STOP: full-screen flight detail is not wired'; exit 1; }
  test -s app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryDetailV46.kt || { echo 'STOP: full-screen flight detail missing'; exit 1; }
fi
grep -Fq 'val inbound: PackageFlightChoice? = candidates(returnOrigin, origin).firstOrNull { compatibleReturn(out.leg, it.leg, scope, firstSaudiCity) }' "$PAIR" || { echo 'STOP: nullable compatible inbound flight-pair fix missing'; exit 1; }
grep -Fq '?: (if (origin != "TAS") candidates(returnOrigin, "TAS").firstOrNull { compatibleReturn(out.leg, it.leg, scope, firstSaudiCity) } else null)' "$PAIR" || { echo 'STOP: compatible fallback return fix missing'; exit 1; }
grep -Fq 'if (inbound != null) return out to inbound' "$PAIR" || { echo 'STOP: nullable return must be checked'; exit 1; }
grep -Fq 'import androidx.compose.ui.graphics.graphicsLayer' "$HOTEL" || { echo 'STOP: correct graphicsLayer import missing'; exit 1; }
if grep -Fq 'import androidx.compose.ui.draw.graphicsLayer' "$HOTEL"; then echo 'STOP: obsolete graphicsLayer import reintroduced'; exit 1; fi
grep -Fq 'note: String?' "$HOTELS" || { echo 'STOP: Hotels hero optional note fix missing'; exit 1; }
grep -Fq 'import androidx.compose.foundation.layout.offset' "$AIRPORTS" || { echo 'STOP: airport-map offset import missing'; exit 1; }
if grep -Fq 'MapLibreMap = map ?: return' "$AIRPORTS"; then echo 'STOP: prohibited airport-map default return reintroduced'; exit 1; fi
if grep -Fq 'MapLibreMap = map ?: return' "$ZIYARATS"; then echo 'STOP: prohibited Ziyarats-map default return reintroduced'; exit 1; fi

echo 'Stage 021 Kotlin production compile-regression checks passed.'
