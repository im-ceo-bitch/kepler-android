from pathlib import Path
p=Path('app/src/main/java/com/iumrah/beta')
read=lambda path:(p/path).read_text()
page=read('data/nuitee/NuiteeHotelService.kt')
assert 'suspend fun hotelPage(' in page and '"pageSize" to NuiteePaging.PAGE_SIZE.toString()' in page
assert 'NuiteePaging.unpriced(hotelPage(' in page
for f in ('ui/hotels/NuiteeHotelsCatalogScreen.kt','ui/trip/NuiteeJourneyHotelsScreen.kt'):
    src=read(f); assert 'rememberNuiteePagedCatalog(' in src and 'page::loadMore' in src and 'page::retry' in src
smart=read('data/smart/SmartConfiguratorService.kt')
assert 'suspend fun applyEdit(' in smart and 'previous !== candidate' in smart and 'candidate.prepareNuiteePackage' in smart and 'candidate.state.value.quote?.bookingAllowed == true' in smart
assert 'quote = null, packageError = null' in read('domain/journey/JourneyStore.kt')
assert 'previous.smartEditCopy()' in read('ui/smart/SmartPackageEditDialog.kt')
account=read('ui/account/AccountScreens.kt'); home=read('ui/home/HomeScreen.kt')
assert 'bookingStore.booking(it.id) != null' in account and 'bookingStore.booking(it.id) != null' in home
assert 'BookingSessionPolicy.sameIdentity(it.pilgrimID ?: it.accountOwnerID, profile?.iumrahID)' in account
assert 'settingsStore::setAutomaticPageHelp' in account and 'homeSettings.automaticPageHelp' in home
offer=read('ui/booking/BusinessOfferScreen.kt'); assert 'service.accept(token,BusinessOfferAccept(value.revision' in offer and 'bookings.adoptOfferBooking' in offer and 'confirmGuestContact' in offer
assert 'BusinessOfferPolicy.validReceipt(packageID, response)' in read('data/booking/BookingStore.kt')
assert 'AppRoute.BusinessOffer -> BusinessOfferScreen' in read('ui/shell/AppShell.kt')
assert 'BusinessOfferPolicy.validToken(offerToken)' in read('MainActivity.kt')
print('Seamless Booking V2/V3 source contracts passed; APK/device/live API verification pending.')
