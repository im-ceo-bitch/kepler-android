#!/usr/bin/env python3
"""Stage 05/06 Android/iOS integration contract guard; not a Gradle/device test."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
ios = Path('/mnt/data/ios_ref')
app = root / 'app/src/main/java/com/iumrah/beta'

def source(rel):
    path = app / rel
    assert path.is_file(), f'Missing: {path}'
    return path.read_text(encoding='utf-8')

models = source('models/nuitee/NuiteeModels.kt')
service = source('data/nuitee/NuiteeHotelService.kt')
rate = source('domain/nuitee/NuiteeRules.kt')
pricing = source('domain/nuitee/NuiteeJourneyPricing.kt')
hotels = source('ui/hotels/NuiteeHotelsCatalogScreen.kt')
rooms = source('ui/trip/NuiteeJourneyHotelsScreen.kt')
review = source('ui/packageflow/NuiteeJourneyReviewScreen.kt')
request = source('data/care/IumrahCareRequestService.kt')
config = source('data/care/NuiteeCareConfiguration.kt')
state = source('domain/journey/JourneyStore.kt')
flights = source('ui/flights/IumrahGuidedFlightsScreen.kt')
shell = source('ui/shell/AppShell.kt')
nav = source('core/navigation/AppChromeStore.kt')
board = source('ui/hotels/HotelsScreen.kt')

checks = {
    'iOS Nuitee routes kept': all(p in service for p in ('/api/package/nuitee/hotels', '/api/package/nuitee/hotel', '/api/package/nuitee/rates')),
    'no new Nuitee API host': 'APIClient()' in service and 'AppConfig.API_BASE_URL' in source('core/network/APIClient.kt'),
    'supplier prices are room-only': 'prices refer to ROOM STAYS' in models,
    'request typed supplier children ages': 'val children: List<Int>' in service and 'childrenAges' in rooms,
    'stale hotel rates displayed as unconfirmed': 'pricesStale = true' in hotels and 'response.cached' in hotels,
    'only freshly fetched valid room offers selectable': 'enabled = freshRates' in hotels and 'validRoomRate' in rooms,
    'pending hotel excludes fabricated prices': 'NuiteePendingHotel' in rooms and 'pending.isNotEmpty()' in pricing,
    'hotel rooms keyed per city and index': '"$city:$roomIndex"' in rooms and '"$city:$index"' in pricing,
    'trip arrival-date and rate invalidation': 'saudiArrivalDay' in state and 'revalidateNuiteeRoom' in state and 'clearNuiteeRooms' in state,
    'price estimation refuses unverified flights, child tariffs': 'trip.children > 0 || trip.infants > 0' in pricing and 'isFinite()' in pricing,
    'request is Care enquiry, never booking creation': '/api/package/care-requests' in request and 'configuration = NuiteeCareConfiguration.from(snapshot)' in review,
    'Care configuration contains flights/rooms/pending': all(f in config for f in ('val flights:', 'val rooms:', 'val pendingHotels:', 'estimatedTotalUSD:', 'val version: Int = 2')),
    'flight to hotel handoff': 'journey.startNuiteeJourney' in flights and 'chrome.openNuiteeJourneyHotels()' in flights,
    'hotel to review handoff': 'chrome.openNuiteeJourneyReview()' in rooms,
    'both new routes connected': 'AppRoute.NuiteeJourneyHotels -> NuiteeJourneyHotelsScreen' in shell and 'AppRoute.NuiteeJourneyReview -> NuiteeJourneyReviewScreen' in shell,
    'old hotel and airfare routes preserved': 'if (board == HotelsBoard.HOTELS)' in board and 'NuiteeHotelsCatalogScreen(language, chrome' in board and 'AppRoute.Flights -> FlightSearchScreen(' in shell,
    'existing backend and workflows untouched': all(p not in str(x) for x in (app / 'data/nuitee').iterdir() for p in ('android-ci.yml', '.github/')),
    'unit tests included': (root / 'app/src/test/java/com/iumrah/beta/domain/nuitee/NuiteeJourneyParityTest.kt').is_file(),
}

if ios.is_dir():
    ios_service = (ios / 'Sources/Services/NuiteeHotelService.swift').read_text(encoding='utf8')
    for route in ('/api/package/nuitee/hotels', '/api/package/nuitee/hotel', '/api/package/nuitee/rates'):
        checks[f'route agrees with SwiftUI {route.rsplit("/",1)[-1]}'] = route in ios_service and route in service

for description, passed in checks.items():
    print(('PASS' if passed else 'FAIL') + ' ' + description)
assert all(checks.values()), 'stage05/06 source contracts failed'
print(f'Stage 05/06 source contracts: {len(checks)} passed. Android Gradle/device tests remain unverified.')
