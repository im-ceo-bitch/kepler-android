#!/usr/bin/env python3
"""Android stage 03/04 parity source contracts. Not a substitute for Gradle/device QA."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/com/iumrah/beta'
onboarding = (base / 'ui/onboarding/OnboardingFlow.kt').read_text()
flights = (base / 'ui/flights/IumrahGuidedFlightsScreen.kt').read_text()
engine = (base / 'ui/flights/GuidedFlightSelectionEngine.kt').read_text()
nav = (base / 'core/navigation/AppChromeStore.kt').read_text()
home = (base / 'ui/home/IumrahHomeSearchHero.kt').read_text()
shell = (base / 'ui/shell/AppShell.kt').read_text()
app = (base / 'IumrahApp.kt').read_text()
unit = (root / 'app/src/test/java/com/iumrah/beta/ui/flights/GuidedFlightSelectionEngineTest.kt').read_text()
checks = {
    'Five actual iOS cinematic onboarding scenes': 'OnboardingPage(' in onboarding and 'OnboardingCarePreview' in onboarding and onboarding.count('OnboardingPage("onboarding_') == 5,
    'Onboarding can be skipped': 'onSkip = ::finish' in onboarding and 'onboarding_skip' in onboarding,
    'Onboarding language and native looping video audio': 'onLanguageChange(item)' in onboarding and 'muted = muted' in onboarding and 'onToggleMute' in onboarding,
    'Onboarding is responsive to narrow phones': 'BoxWithConstraints' in onboarding and 'maxHeight < 730.dp' in onboarding,
    'Flight first uses existing sources': 'curated.load(trip)' in flights and 'discovery.offers(' in flights,
    'Flight first has real detail and two legs': 'Dialog(onDismissRequest = { detail = null }' in flights and 'IumrahPremiumFlightDetail(' in flights and 'stepReturn' in flights and 'GuidedFlightDetail' in flights,
    'Flight first rejects mixed sources': 'outbound.source != inbound.source' in engine and 'GuidedFlightRules.compatible(outbound, inbound)' in flights,
    'Published flight has no fabricated fare': 'override val priceUsd: Double? = null' in engine,
    'Economy only Data API': 'FlightCabinClass.ECONOMY' in engine and 'row.price > 0' in engine,
    'Data API is re-quoted server side': 'journey.stageAviasalesFlight(first.offer)' in flights and 'journey.stageAviasalesFlight(back.offer, returnLeg = true)' in flights,
    'Published flights use server validated selections': 'journey.commitTripBuilder(nextTrip, PackageFlightPath.PUBLISHED_DIRECT' in flights,
    'Home opens guided flow, old flow is preserved': 'chrome.openGuidedFlights()' in home and 'fun openFlights()' in nav,
    'Route supports background and UI': 'AppRoute.GuidedFlights -> IumrahGuidedFlightsScreen(' in shell and 'AppRoute.GuidedFlights -> "flight_builder"' in app,
    'Unit tests cover provider mismatch': 'published and data api sources never mix' in unit,
    'No new remote API endpoints': '/api/' not in flights,
}
failed = [key for key, ok in checks.items() if not ok]
for label, success in checks.items(): print(('PASS' if success else 'FAIL') + ': ' + label)
if failed: sys.exit('Stage03/04 source checks failed: ' + ', '.join(failed))
print('Source parity contract passed; APK compilation and device screenshots remain unverified.')
