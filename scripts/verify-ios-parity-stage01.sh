#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
root=Path('app/src/main/java/com/iumrah/beta')
ui=(root/'ui/ziyarats/ZiyaratStableScreen.kt').read_text()
entry=(root/'ui/ziyarats/ZiyaratJourneyScreen.kt').read_text()
service=(root/'data/ziyarat/ZiyaratService.kt').read_text()
seed=(root/'data/ziyarat/ZiyaratFallbackCatalog.kt').read_text()
flight=(root/'ui/flights/FlightDiscoveryDetailV46.kt').read_text()
link=(root/'data/flight/AviasalesSearchLinkBuilder.kt').read_text()
assert 'ZiyaratStableScreen(language = language, chrome = chrome, service = service)' in entry
assert 'ZiyaratFallbackCatalog.route' in entry
assert 'ZiyaratFallbackCatalog.route' in service and 'timeoutSeconds = 10L' in service
assert 'ZiyaratFallbackCatalog.route(city.apiName)' in ui
assert 'PullToRefreshBox' in ui
assert 'if (showMap)' in ui and 'rememberIumrahMapController()' in ui
assert all(f'Site("{id}"' in seed for id in ('haram','thawr','arafat','muzdalifah','mina','jamarat','quba','qiblatain','uhud','baqi','shajara'))
assert 'bookingUrl' not in flight
assert 'Data API' not in flight
assert 'AviasalesSearchLinkBuilder.searchUrl(' in flight
assert 'FlightDiscoveryItineraryValidator.verified(' in flight
assert 'https://www.aviasales.ru/search/' in link
assert 'testDebugUnitTest' in Path('.github/workflows/android-ci.yml').read_text()
print('Stage 01: targeted Ziyarats/Aviasales source parity invariants passed.')
PY
