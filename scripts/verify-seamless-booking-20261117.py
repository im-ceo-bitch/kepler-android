from pathlib import Path
root=Path(__file__).resolve().parent.parent / "app/src/main/java/com/iumrah/beta"
def src(p): return (root/p).read_text()
checkout=src("ui/booking/BookingCheckoutScreen.kt")
store=src("data/booking/BookingStore.kt")
service=src("data/booking/BookingService.kt")
account=src("ui/account/AccountScreens.kt")
status=src("ui/booking/PilgrimCheckoutScreen.kt")
model=src("models/booking/BookingSharedModels.kt")
assert checkout.index("confirmGuestContact") < checkout.index("bookingStore.create(")
assert "BookingCreationNotificationIntent" in model and "notificationIntent = notificationIntent" in store
assert "submitted = true // never replay" in checkout and "committed ?: run" in checkout
assert "attachGuestContact(session.id" in checkout and "recordVerifiedContact" in checkout
assert "/api/package/booking-contact/sms/start" in service and "/api/package/booking-contact/email/start" in service
assert "BookingPasswordlessAccessCard(language, null" in account and "key(register) { GuestLoginCard(" not in account
assert "BookingPasswordlessAccessCard(language, session" in status and "Fill in manually" not in status
assert 'check(response.ok) { "Passport upload rejected" }' in src("data/account/IumrahAccountService.kt")
assert "BookingSessionPolicy.sameIdentity(it.pilgrimID, owner)" in store
assert "pushMutex.withLock" in store and "response.ok &&" in store
assert "careChatKeyForPush" in store and "defaultCareChatKey" in src("ui/care/CareHomeScreen.kt")
print("Seamless Booking source contracts passed; no Android compilation or live API guarantee.")
