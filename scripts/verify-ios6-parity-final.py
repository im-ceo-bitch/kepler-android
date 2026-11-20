#!/usr/bin/env python3
"""iOS 6 client migration static checks. Not a device/screenshot/Gradle build."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/src/main/java/com/iumrah/beta'
def source(name): return (root/name).read_text()
review=source('ui/packageflow/PackageReviewChrome.kt')
for s in ['R.drawable.about_iumrah_kaaba_corner', '168.dp', 'RoundedCornerShape(24.dp)',
          'TripStayPlanner.breakdown(trip)', 'TripReviewPriceGrouping.groups(price.components)', 'ReviewPriceDistribution(',
          'servicesSubtotalUsd','supportAmountUsd','paymentAmountUsd','roundingUsd']:
    assert s in review, s
pricing=source('domain/pricing/TripReviewPriceGrouping.kt')
for s in ['linkedMapOf','startsWith("flight_")','"vip_transfer_upgrade"',
          'startsWith("ziyarat_")','component.amountUsd','add(component.amountUsd)']:
    assert s in pricing, s
for file in ['ui/packageflow/FinalPackageScreen.kt','ui/packageflow/NuiteeJourneyReviewScreen.kt']:
    text=source(file)
    for marker in ['ReviewHeader(', 'ReviewVerifiedPrice(', 'ReviewPriceDistribution(', 'BrandComparisonEntry(', 'ReviewBookingBar(']:
        assert marker in text, (file,marker)
assert 'HomeBrandSlogan(language)' in source('ui/care/CareHomeScreen.kt')
assert 'HomeBrandSlogan(language)' in source('ui/account/AccountScreens.kt')
assert 'BrandComparisonEntry(language,chrome)' in source('ui/packageflow/SavedPackageScreens.kt')
notifications=source('data/notification/ClientNotificationStore.kt')
for s in ['bookingReceipts', 'syncBookingReceipts(', 'local-booking-created:', 'readReceiptIDs',
          'return // Local receipt must not call remote /read API.']:
    assert s in notifications, s
assert 'syncBookingReceipts(bookingState.sessions, settings.language)' in source('IumrahApp.kt')
assert 'bookings.canOpenBookingFromPush(it)' in source('ui/notifications/NotificationsScreen.kt')
account=source('data/account/IumrahAccountStore.kt')
service=source('data/account/IumrahAccountService.kt')
assert 'service.deleteAccount(current)' in account and 'if (token == current) clearLocalSession()' in account
assert 'api.delete(IumrahAccountRoutes.PROFILE, bearer(token))' in service
assert 'ACCOUNT_HAS_FINANCIAL_HISTORY' in source('ui/account/AccountScreens.kt')
print('iOS 6 final client source-contract checks passed (source-only, not APK/device assurance)')
