#!/usr/bin/env python3
"""Strict P00/P02 source-contract checks against the supplied iOS6 tree.

This is source cross-reference, NOT screenshot, Compose compile or API E2E QA.
Run: python3 scripts/qa/p00/verify_ios6_p00.py /path/to/kepler-ios-main
"""
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[3]
android = root / 'app/src/main/java/com/iumrah/beta'

def read(rel): return (android / rel).read_text()

checks = []
def expect(condition, name):
    if not condition: raise AssertionError(name)
    checks.append(name)

chrome = read('core/navigation/AppChromeStore.kt')
shell = read('ui/shell/AppShell.kt')
hotels = read('ui/hotels/HotelsScreen.kt')
nuitee = read('ui/hotels/NuiteeHotelsCatalogScreen.kt')
header = read('ui/components/IumrahRootPageHeader.kt')
booking = read('ui/booking/BookingsHomeScreen.kt')

expect('fun startNewTrip() = openDiscoveryBuild()' in chrome, 'No legacy TripBuilder for public new-trip CTA')
expect('fun openGuidedFlights() = push(AppRoute.GuidedFlights, tab = AppTab.HOME)' in chrome,
       'Home owns guided flight navigation')
expect('fun findHotels(city: String, checkin: LocalDate, nights: Int, adults: Int,' in chrome,
       'Complete iOS Home->Hotels hotel-search contract')
expect('old.hotelSearch?.revision != revision' in chrome and 'hotelSearchRevision = revision' in chrome,
       'Hotel-search event revisions and stale ack guard')
expect('tabNavigation: Map<AppTab, TabNavigationState>' in chrome and 'fun back(): Boolean' in chrome,
       'Independent TabView back stacks')
expect('rememberSaveableStateHolder()' in shell and 'SaveableStateProvider(tab)' in shell,
       'TabView saveable composition state')
expect('.clipToBounds()' in shell and 'height(statusInset)' in shell and
       'val rootHeaderVisible =' in shell and 'isAppearanceLightStatusBars = !rootHeaderVisible' in shell, 'Moving root status surface follows header visibility')
expect('Modifier.fillMaxWidth().statusBarsPadding().height(headerHeight)' in header and
       'reportMastheadVisibility' in header, 'Header compensates safe area and reports actual scroll bounds')
expect('rememberTextMeasurer()' in shell and '(105 downTo 76)' in shell,
       'Intrinsic width-based tab label fitting')
expect('"Профиль"' in shell and '"Profil"' in shell, 'Account tab uses actual iOS Profile label')
expect('if (searchChrome.hotelSearch != null) board = HotelsBoard.HOTELS' in hotels,
       'Home search overrides restored Flights board')
expect('firstRoom.childrenAges' in nuitee and 'firstRoom.adults' in nuitee and 'childrenAges == request.childrenAges' in nuitee,
       'Room quote carries supplier-specific occupancy without treating it as family total')
expect('if (replay && identicalQuery) refreshing++' in nuitee,
       'No duplicate initial rate hydration; exact repeated search refreshes once')
expect('chrome.consumeHotelSearch(request.revision)' in nuitee,
       'Catalogue consumes search only after application')
expect('bottom = 112.dp' in booking, 'Booking loading/error state clears floating navigation')
expect('fun beginFamilyJourney(value: JourneyStore)' in chrome and
       'journeyForRoute(route: AppRoute' in chrome, 'Family journey isolated from global Guided Home store')
expect('discoveryFlightsRevision = old.discoveryFlightsRevision + 1' in chrome and
       'LaunchedEffect(searchChrome.discoveryFlightsRevision)' in hotels, 'Repeating Flights command is observed in composed tab')
expect('manualSnapshot.selectRoom(choice)' in nuitee and 'familyQuoteRequired' in nuitee,
       'Adult-only catalogue rooms enter shared manual basket; family quote guarded')

if len(sys.argv) > 1:
    ios = Path(sys.argv[1]).resolve()
    def swift(rel): return (ios / 'Sources' / rel).read_text()
    rootview = swift('Views/RootView.swift')
    native_tabbar = swift('AppShell/IumrahStoreTabBar.swift')
    ios_chrome = swift('AppShell/AppChromeStore.swift')
    ios_home = swift('Views/Home/HomePersonalizationSections.swift')
    ios_root = swift('Core/Components/IumrahRootScrollingPage.swift')
    expect('TabView(selection: $chrome.currentTab)' in rootview and
           'IumrahStoreTabBar(' not in rootview,
           'Actual current iOS root uses native TabView, not dormant custom bar')
    expect('struct IumrahStoreTabBar' in native_tabbar,
           'Dormant custom tab bar exists but is not app root')
    expect('shouldStartTripBuilder = false' in ios_chrome and 'openUmrahPlan()' in ios_chrome,
           'Current iOS public new-trip route')
    expect('requestedHotelTabSearch = search' in ios_chrome and
           'let checkin: Date' in ios_chrome and 'let nights: Int' in ios_chrome,
           'Actual iOS hotel search transfer contract')
    expect('value: 14, to: Date()' in ios_home and 'nights: 7, adults: 2' in ios_home,
           'Actual iOS Home destinations defaults')
    expect('viewport.safeAreaInsets.top' in ios_root and
           '.ignoresSafeArea(.container, edges: extendsUnderStatusBar ? .top : [])' in ios_root,
           'Actual iOS scrolling header safe-area model')

print(f'PASS: {len(checks)} P00/P02 cross-platform source checks')
print('LIMIT: No Android compile, live supplier calls, or pixel comparison in this test.')
