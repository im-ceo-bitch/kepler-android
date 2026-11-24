#!/usr/bin/env python3
"""V8 source contract against November 14 SwiftUI. Not a rendered screenshot/build test."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/com/iumrah/beta'
count = 0

def read(path): return (BASE / path).read_text(encoding='utf-8')
def check(ok, message):
    global count
    assert ok, message
    count += 1
    print('PASS:', message)

def ordered(source, items):
    return all(item in source for item in items) and [source.index(item) for item in items] == sorted(source.index(item) for item in items)

p = argparse.ArgumentParser()
p.add_argument('--ios', type=Path)
args = p.parse_args()
theme = read('core/design/IumrahTheme.kt')
header = read('ui/components/IumrahRootPageHeader.kt')
pills = read('ui/components/IumrahHeaderPills.kt')
home = read('ui/home/HomeScreen.kt')
search = read('ui/home/IumrahHomeSearchHero.kt')
hotels = read('ui/hotels/NuiteeHotelsCatalogScreen.kt')
flights = read('ui/hotels/HotelsScreen.kt')
booking = read('ui/booking/BookingsHomeScreen.kt')
care = read('ui/care/CareHomeScreen.kt')
account = read('ui/account/AccountScreens.kt')
shell = read('ui/shell/AppShell.kt')
for metric, value in {'DiscoveryHeaderExtra':52, 'ActionHeaderExtra':34, 'HeaderPillHeight':44, 'HeaderPillViewport':46, 'HeaderPillGap':8, 'HeaderPillHorizontal':15, 'HeaderPillIcon':15, 'SearchOverlap':40}.items():
    check(f'val {metric} = {value}.dp' in theme, f'SwiftUI geometry {metric}={value}')
check('discovery -> IumrahDesign.DiscoveryHeaderExtra' in header and 'footer != null -> IumrahDesign.ActionHeaderExtra' in header, 'Header height follows actual discovery/action state')
check('Modifier.fillMaxWidth().statusBarsPadding()' in header and '.then(if (flexibleFooter) Modifier else Modifier.height(headerHeight))' in header and 'IumrahSignatureWorldBackground(Modifier.matchParentSize()' in header, 'Black header owns moving status inset')
check('roundedBottom || discovery' in header and 'isAnimating = animateMap && !discovery' in header, 'Discovery curvature and stationary world atlas')
check('LazyRow(' in pills and 'listState.scrollToItem(index)' in pills and 'itemsIndexed(pills' in pills, 'Scrollable labels and visible active action')
check('.selectable(selected = active, enabled = pill.enabled, role = Role.Button)' in pills, 'Accessible selection and disabled actions')
check('Color.White.copy(alpha = .07f)' in pills and 'if (active) 1f else .65f' in pills, 'Independent white outlined capsules')
check(ordered(pills, ['"Собрать умру"', '"Отели"', '"Авиабилеты"', '"Трансфер"', '"Мой пакет"']), 'Five discovery labels in source order')
for screen, source, selection in [('Home',home,0), ('Hotels',hotels,1), ('Flights',flights,2)]:
    check(f'IumrahDiscoveryPills(language, {selection})' in source and 'discovery = true' in source, f'{screen}: shared masthead actually used')
    check('chrome.openDiscoveryTransfer()' in source and 'chrome.openDiscoveryPackage()' in source, f'{screen}: existing real routes')
check('IumrahOverlappingMasthead(header = {' in home and 'IumrahOverlappingMasthead(header = {' in hotels, 'Home and hotel search share measured overlap')
check('masthead.height + card.height - overlap' in pills and 'card.placeRelative(0, masthead.height - overlap, zIndex = 1f)' in pills, 'Search card in parent bounds, topmost hit target')
check('searchExpanded = true' in home and 'onExpandedChange(!expanded)' in search and 'chrome.consumeDiscoveryBuild()' in home, 'Header Build controls the real search')
check(ordered(home, ['item { SmartConfiguratorHomeCard(', 'item { EmotionalPrompt(', 'item { HomeVideoCarousel(', 'item { IumrahServicesCarousel(', 'item { JourneyTravelInfoPanel(', 'item { AudienceSection(', 'item { HomeIntegrationsSection(']), 'Current Home marketing order')
check('item { ReadyPackagesSection(' not in home, 'No historical ready packages on Home')
check('LocalDate.now().plusDays(14)' in hotels and 'searchRequest?.nights ?: 7' in hotels and 'searchRequest?.checkin' in hotels, 'Hotel discovery defaults and full Home search handoff')
check('footer = { BookingPanelPicker(language, panel, onPanel) }' in booking and '"Статус брони"' in booking and '"Документы"' in booking, 'Booking tabs in masthead, same state')
check('CupertinoSymbol.Message, activeSession != null' in care and '2 -> openChat()' in care, 'Care chat is disabled without booking')
check('BookingPasswordlessAccessCard(language, null, accountStore' in account and 'key(register) { GuestLoginCard(' not in account and 'footer = guestFooter' not in account, 'Guest account root exposes booking recovery, not registration')
check('initialBoard = if (chromeState.discoveryFlights)' in shell and 'Role.Tab' in shell and 'Box(Modifier.height(25.dp)' in shell, 'Root Flights stays on tabs; bottom icon slot and selection semantics')
if args.ios:
    i = args.ios / 'Sources'
    swift = (i/'Core/Components/IumrahSignatureHeaders.swift').read_text()
    h = (i/'Views/Home/HomeDashboardView.swift').read_text()
    hotel = (i/'Views/Tabs/HotelsHomeView.swift').read_text()
    a = (i/'AppShell/IumrahAccountView.swift').read_text()
    check(all(x in swift for x in ['contentHeight + 52', 'footer == nil ? 0 : 34', '.frame(height: 44)', '.frame(height: 46)', 'HStack(spacing: 8)', '.padding(.horizontal, 15)', '.system(size: 15, weight: .medium)', 'isAnimating: !discovery']), 'Actual iOS header metrics independently verified')
    check(ordered(h, ['HomeEmotionalJourneyPrompt()', 'HomeVideoCarousel()', 'IumrahHomeServicesSection(', 'SmartConfiguratorHomeCard {', 'JourneyTravelInfoView(showsPageSelector: true)', 'IumrahHomeAudienceSection(language:', '\n                homeIntegrationsSection']), 'Actual iOS Home sequence independently verified')
    check('value: 14, to: Date()' in hotel and 'private var hotelNights = 7' in hotel and 'searchCardOverlap: 40' in hotel, 'Actual iOS hotel defaults independently verified')
    check('guestAccountMode: IumrahGuestAccountMode = .signIn' in a, 'Actual iOS guest default independently verified')
print(f'V8 source checks: {count} passed. No APK or device visual certification.')
