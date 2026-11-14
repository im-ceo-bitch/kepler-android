#!/usr/bin/env python3
"""Stage 01/02 read-only iOS->Android source/asset contract.

This is not a Kotlin compiler, emulator, pixel-diff, or backend acceptance test.
Usage: python3 scripts/verify-ios-stage01-stage02.py [--ios /path/to/kepler-ios-main]
"""
import argparse
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
pkg = root / 'app/src/main/java/com/iumrah/beta'


def check(value, description):
    if not value:
        raise AssertionError(description)
    print('PASS:', description)


def source(path):
    return (pkg / path).read_text(encoding='utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ios', type=Path, default=None)
    args = ap.parse_args()
    theme = source('core/design/IumrahTheme.kt')
    header = source('ui/components/IumrahRootPageHeader.kt')
    home = source('ui/home/HomeScreen.kt')
    search = source('ui/home/IumrahHomeSearchHero.kt')
    helpcard = source('ui/home/IumrahHomeHelpCard.kt')
    dates = source('ui/trip/FlightDateCalendarDialog.kt')
    shell = source('ui/shell/AppShell.kt')
    journey = source('domain/journey/JourneyStore.kt')
    nav = source('core/navigation/AppChromeStore.kt')
    services = source('ui/home/IumrahServicesScreen.kt')
    for name, metric in [('PagePadding','18.dp'), ('CardRadius','28.dp'), ('HeroRadius','34.dp'),
                         ('CompactRadius','19.dp'),('ControlHeight','56.dp'),
                         ('SignatureLogoWidth','187.2f.dp'),('SignatureLogoHeight','57.6f.dp'),
                         ('SignatureHeaderTop','14.dp'),('SignatureHeaderHeight','171.6f.dp'),
                         ('SignatureBottomRadius','30.dp'),('SearchOverlap','40.dp')]:
        check(f'val {name} = {metric}' in theme, 'iOS design token '+name)
    check('val RoyalViolet = Color(0xFF6636D9)' in theme and
          'val SignatureBlack = Color(0xFF090909)' in theme and
          'val LightPage = Color(0xFFF2F2F7)' in theme, 'current iOS signature and accent palette')
    check('Font(R.font.lato_regular' in theme and 'Font(R.font.lato_bold' in theme,
          'Lato Regular/Bold is application typography')
    check('SignatureHeaderButton(CupertinoSymbol.Menu, 48.dp, 0, chrome::openSidebar)' in header and
          'Modifier.width(logoWidth).height(IumrahDesign.SignatureLogoHeight)' in header and
          '48.dp, unreadCount, chrome::openNotifications' in header,
          'centered wordmark with symmetrical menu and notification chrome')
    check('val bottomRadius = if (roundedBottom) IumrahDesign.SignatureBottomRadius else 0.dp' in header,
          'Home-only curved signature header; other tabs straight')
    check('IumrahSignatureWorldBackground(Modifier.matchParentSize(), mapOpacity = .33f)' in header,
          'animated world map preserved')
    check('Modifier.layout { measurable, constraints ->' in home and
          'child.placeRelative(0, -overlap)' in home and
          '(child.height - overlap).coerceAtLeast(0)' in home,
          'search overlaps header by 40dp WITHOUT reserving an invisible spacer')
    check('PullToRefreshBox(' in home and 'bookingStore.refreshAll()' in home and
          'notifications.refresh(accountToken)' in home and 'refreshVersion += 1' in home,
          'actual pull-to-refresh reloads Home inventories and notifications')
    check('IumrahHomeSearchHero(language, journeyState.trip, journey, airports, curatedFlights, chrome)' in home,
          'Home search receives real journey and network data services')
    check('ConfiguratorAirportSelector(' in search and
          'journey.updateTrip(trip.copy(origin = selected.iata, originAirport = selected))' in search,
          'departure airport edits real JourneyStore')
    check('JourneyScope.MAKKAH_ONLY' in search and 'JourneyScope.MAKKAH_AND_MADINAH' in search and
          'SaudiArrivalAirport.MADINAH' in search and
          'journey.updateTrip(trip.copy(scope = scope' in search,
          'route and Saudi-arrival selectors edit real itinerary')
    check('publishedOnly = true' in search and 'curatedFlights = curatedFlights' in search and
          'journey.updateTrip(newTrip)' in search and 'journey.setPublishedSelection()' in search,
          'published calendar edits dates but never auto-books a flight')
    check('publishedOnly: Boolean = false' in dates and
          'val canApply = hasValidRange && (!publishedOnly || selectedPublished != null)' in dates and
          'if (!publishedOnly) showFlexibleWarning = true' in dates,
          'published-only guard enforced on Home while older builder is unchanged')
    check('ModalBottomSheet(' in search and
          'trip.copy(adults = adults, children = children, infants = infants' in search and
          'infants = infants.coerceAtMost(adults)' in search and
          'minOf(adults, 9 - adults - children)' in search,
          'traveler sheet enforces adult/infant and maximum party limits')
    check('journey.commitTripBuilder(' in search and 'chrome.openGuidedFlights()' in search and
          'fun openGuidedFlights() = push(AppRoute.GuidedFlights' in nav and
          'PackageFlightPath.PUBLISHED_DIRECT' in search,
          'real flight-catalog route and shared trip builder, no mock prices')
    check('fun updateTrip(value: TripDraft)' in journey and 'fun commitTripBuilder(' in journey,
          'shared state methods used by current Android configurator')
    check('var expanded by remember' in helpcard and 'AnimatedVisibility(' in helpcard and
          '"Что делать сейчас?"' in helpcard and '"What should I do now?"' in helpcard,
          'localized, initially collapsed iOS HelpCard')
    check(home.index('item { IumrahHomeHelpCard(language) }') <
          home.index('item { EmotionalPrompt('), 'iOS Home help shown before marketing')
    check('refreshVersion' in home and 'LaunchedEffect(origin, refreshVersion)' in home,
          'storefront refresh actually invalidates cached package carousel')
    check('AppTab.HOME -> HomeScreen(language, chrome, hotelCatalog, journey, airports, curatedFlights, bookingStore, notifications, accountStore.bearerToken)' in shell,
          'all new Home dependencies injected from existing AppShell')
    codes = re.findall(r'^\s+([A-Z]+)\("([a-z]+)", R.drawable.',services,re.M)
    check([code for _, code in codes] ==
          ['visa','flights','hotels','transfer','guide','ziyarat','haramain','support','digital'],
          'nine iOS service categories and stable deep-link IDs')
    for file in ('iumrah_visa_services_hero.jpg','iumrah_services_flight_hero.jpg'):
        check((root/'app/src/main/res/drawable-nodpi'/file).stat().st_size > 50000,
              'bundled photographic service asset '+file)
    check('item { IumrahServicesCarousel(language, chrome) }' in home and
          'fun openIumrahServices(initial: String' in nav,
          'Home service cards link to expanded service detail')
    if args.ios:
        ios = args.ios / 'Sources'
        assert ios.is_dir(), f'Not an iOS source tree: {ios}'
        geometry = (ios/'Core/Components/IumrahSignatureHeaders.swift').read_text()
        swiftHome = (ios/'Views/Home/HomeDashboardView.swift').read_text()
        swiftHelp = (ios/'Core/Components/IumrahPageHelpCard.swift').read_text()
        for token in ('logoWidth: CGFloat = 187.2','logoHeight: CGFloat = 57.6',
                      'topInset: CGFloat = 14','lowerInset: CGFloat = 100',
                      'bottomRadius: CGFloat = 30'):
            check(token in geometry, 'iOS source geometry '+token)
        for token in ('IumrahHomeJourneySearchHero','searchCardOverlap: 40',
                      'publishedOnly: true','IumrahPageHelpCard("home.default")'):
            check(token in swiftHome, 'current source Home '+token)
        check('private var expanded = false' in swiftHelp,
              'iOS HelpCard is initially collapsed')
    print('STAGES 01+02 Android source/asset contract: PASS. Android CI and visual QA still mandatory.')

if __name__ == '__main__':
    main()
