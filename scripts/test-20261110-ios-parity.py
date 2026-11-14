#!/usr/bin/env python3
"""Offline contract gates for November iOS -> Android changes (does not claim a Gradle build)."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
app = root / 'app/src/main/java/com/iumrah/beta'
ios = Path(__file__).resolve().parents[3] / 'kepler-ios-main'

def src(path):
    return (app / path).read_text(encoding='utf-8')

analytics = src('core/analytics/IumrahIntelligence.kt')
assert 'AppConfig.INTELLIGENCE_EVENTS_URL' in analytics
assert 'INTELLIGENCE_EVENTS_PATH = "/api/catalog/hotels/intelligence/events"' in src('core/config/AppConfig.kt')
for token in ('"platform", "android"', '"visitorId"', '"sessionId"', '"occurredAt"', '"durationMs"', '"enabled"', '"pending"', 'connection.responseCode == 202', 'take(20)', 'takeLast(150)', '16_384', 'MAX_AGE_MS', '"screen_exit"'):
    assert token in analytics, f'Missing telemetry contract: {token}'
assert 'booking_completed' not in analytics, 'Checkout is not payment completion.'
assert not re.search(r'password|passport|paymentCard|advertisingId|androidId|ime[iI]', analytics), 'Never collect sensitive data.'

root_code = src('IumrahApp.kt')
for token in ('IumrahIntelligence.start(', 'IumrahIntelligence.screenChanged(', 'IumrahIntelligence.enterBackground(', 'IumrahIntelligence.resume(', '"onboarding_completed"'):
    assert token in root_code, token

checkout = src('ui/booking/BookingCheckoutScreen.kt')
assert checkout.index('bookingStore.create(') < checkout.index('IumrahIntelligence.record("booking_submitted"'), 'Only log a booking created on confirmed server success'
assert '"booking_completed"' not in checkout
assert '"booking_submit"' in checkout

service = src('ui/home/IumrahServicesScreen.kt')
ids = re.findall(r'^\s+([A-Z]+)\("([a-z]+)", R.drawable.', service, re.M)
assert [code for name, code in ids] == ['visa','flights','hotels','transfer','guide','ziyarat','haramain','support','digital'], ids
for drawable in ['iumrah_visa_services_hero.jpg','iumrah_services_flight_hero.jpg']:
    assert (root / 'app/src/main/res/drawable-nodpi' / drawable).stat().st_size > 50000

home = src('ui/home/HomeScreen.kt')
assert 'item { IumrahServicesCarousel(language, chrome) }' in home
assert 'IumrahHomeSearchHero(language, journeyState.trip, journey, airports, curatedFlights, chrome)' in home
assert 'item { ServicesSection(language, chrome) }' not in home
assert 'item { HotelFirstPackagesSection(' not in home

nav = src('core/navigation/AppChromeStore.kt')
shell = src('ui/shell/AppShell.kt')
assert 'fun openIumrahServices(initial: String' in nav
assert 'is AppRoute.IumrahServices -> IumrahServicesScreen' in shell
assert 'AppRoute.AccountAnalytics -> AccountAnalyticsScreen' in shell
account = src('ui/account/AccountScreens.kt')
assert account.count('chrome.openAccountAnalytics()') == 2, 'Settings for both guests and signed-in users'

# If the contemporary SwiftUI checkout reference is adjacent, verify parity vocabulary.
if ios.exists():
    ios_service = (ios/'Sources/Views/Home/IumrahServicesView.swift').read_text()
    for _, code in ids:
        assert f'case .{code}' in ios_service, f'Service {code} missing from iOS reference'
print('November 10 iOS→Android contract check: PASS (source/asset, NOT APK compilation)')
