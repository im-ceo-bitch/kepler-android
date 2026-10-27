#!/usr/bin/env python3
"""Android Booking source contract vs untouched iOS; not a runtime or screenshot test."""
from pathlib import Path
import argparse

root=Path(__file__).resolve().parent.parent
base=root/'app/src/main/java/com/iumrah/beta'
detail=(base/'ui/booking/BookingDetailScreen.kt').read_text()
home=(base/'ui/booking/BookingsHomeScreen.kt').read_text()
calendar=(base/'ui/booking/BookingItineraryCalendarAndroid.kt').read_text()
model=(base/'models/booking/BookingSharedModels.kt').read_text()

def check(expr, reason):
    if not expr: raise AssertionError(reason)
    print('PASS',reason)

check('ClipData.newPlainText(label, value)' in detail and 'ClipboardManager' in detail and 'enabled = value.isNotBlank() && value != "—"' in detail, 'Booking-number/iumrah ID copy buttons really copy and disable placeholders')
check('val displayPilgrimID: String?' in model and 'digits.padStart(8, \'0\')' in model and 'session.displayPilgrimID' in detail, 'Display ID normalized to 8 digits, raw API identifier preserved')
check('LaunchedEffect(bookingID, language)' in detail and 'localizeServerItinerary(it, language)' in detail, 'Itinerary reloads and relocalizes when language changes')
for value in ['PAYMENT_PENDING','PENDING_PAYMENT','WAITING_PAYMENT','AWAITING_PAYMENT','PAYMENT_AND_DATA_PENDING','AWAITING_PAYMENT_AND_DATA']:
    check(value in detail, 'Server payment status recognized: '+value)
check('bookingNeedsCheckout(session)' in detail and 'if (bookingNeedsCheckout(session)) {' in detail and 'chrome.openPilgrimCheckout(session.id)' in detail, 'All waiting-payment statuses expose real checkout flow')
check('if (bookingNeedsCheckout(session)) {\n                    BookingManualPaymentNotice' in detail, 'Waiting-payment statuses show invoice and refund notice')
check('onOpenFullSchedule = { selectedPage = BookingPrimaryPageAndroid.SCHEDULE }' in detail and 'onOpenFullSchedule: (() -> Unit)? = null' in calendar, 'Booking itinerary preview opens full schedule')
check('dayItems.take(4)' in calendar and 'visibleDayItems.forEachIndexed' in calendar, 'Preview shows at most four events, full schedule retains all')
check('PullToRefreshBox(' in home and 'try { refresh() } finally { isRefreshing = false }' in home and 'delay(60_000)' in home, 'Bookings support both manual pull-to-refresh and existing periodic refresh')
check('BookingStatusFlights(' in home and 'BookingFulfillmentCenter(' in home and 'BookingItineraryCalendarAndroid(' in home and 'RootTelegramCompactCard(' in home, 'Booking status, itinerary and Telegram entry preserved')
check('onDelete = { id ->' in home and 'bookingStore.deleteBooking(id)' in home, 'Existing deletion business operation preserved')
parser=argparse.ArgumentParser();parser.add_argument('--ios',type=Path);args=parser.parse_args()
if args.ios:
    iosdetail=(args.ios/'Sources/Views/Booking/BookingDetailView.swift').read_text()
    ioscal=(args.ios/'Sources/Views/Booking/BookingItineraryCalendarView.swift').read_text()
    ioshome=(args.ios/'Sources/Views/Tabs/BookingsHomeView.swift').read_text()
    iosmodel=(args.ios/'Sources/Models/BookingModels.swift').read_text()
    check('UIPasteboard.general.string = value' in iosdetail, 'Read-only iOS uses the same clipboard action')
    check('onOpenFullSchedule' in ioscal and 'prefix(4)' in ioscal, 'Read-only iOS uses a four-event preview with navigation')
    check('.refreshable' in ioshome and '.seconds(60)' in ioshome, 'Read-only iOS supports pull-to-refresh and polling')
    check('String(repeating: "0", count: 8 - digits.count)' in iosmodel, 'Read-only iOS uses eight-digit pilgrim identifier')
print('Block 6 Android Booking source contract OK. Build/device QA still open.')
