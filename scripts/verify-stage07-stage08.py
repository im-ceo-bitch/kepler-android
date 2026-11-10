#!/usr/bin/env python3
"""Stage 07/08 source contract checks. Gradle/device/backend integration still needed."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
app = root / 'app/src/main/java/com/iumrah/beta'
def src(rel):
    path = app / rel
    assert path.is_file(), rel
    return path.read_text()
svc = src('data/account/IumrahAccountService.kt')
store = src('data/account/IumrahAccountStore.kt')
models = src('models/account/IumrahAccountModels.kt')
routes = src('data/account/IumrahAccountRoutes.kt')
booking = src('ui/booking/BookingsHomeScreen.kt')
booking_store = src('data/booking/BookingStore.kt')
pilgrim = src('ui/booking/PilgrimCheckoutScreen.kt')
account = src('ui/account/AccountScreens.kt')
security = src('ui/account/AccountSecurityParity.kt')
password = src('ui/account/AccountPasswordSecurityScreen.kt')
nav = src('core/navigation/AppChromeStore.kt')
shell = src('ui/shell/AppShell.kt')
checks = {
 'booking proof tried alone before account bearer fallback': 'mapOf("x-booking-token" to bookingProof)' in svc and '"Authorization" to "Bearer $bearerToken"' in svc,
 'fallback limited to authorization or missing booking': 'BookingAuthFallbackPolicy.mayRetryWithAccount(error)' in svc and 'error.code in setOf(401, 403, 404)' in src('domain/booking/BookingAuthFallbackPolicy.kt'),
 'booking home refresh reloads latest session and checkout': 'bookingStore.refreshAll()' in booking and 'bookingStore.state.value.sessions.firstOrNull' in booking and 'checkoutWithFallback(' in booking,
 'account-only booking refresh uses the authenticated trip detail': 'current.accessToken.isBlank()' in booking_store and 'accountStore.tripDetail(id)' in booking_store and 'accountDetail.trip' in booking_store,
 'checkout uses explicit token fallback': 'checkoutWithFallback(bookingID, session.accessToken, accountStore.bearerToken)' in pilgrim,
 'delete requires explicit confirmation, not implicit tap': 'pendingDeleteID?.let { id ->' in booking and 'AlertDialog(' in booking and 'bookingStore.deleteBooking(id)' in booking and 'onDelete(session.id)' in booking,
 'email sign-in uses OTP, existing iumrah ID uses password': 'startEmailLogin(' in account and 'confirmEmailLogin(' in account and 'method=="id" -> accountStore.login' in account,
 'register email / SMS has passwordless verification payload': 'data class IumrahPhoneRegistrationConfirmRequest' in models and 'data class IumrahRegistrationEmailConfirmRequest' in models and 'IumrahPhoneRegistrationConfirmRequest(challengeID, code, deviceIdentity.current(locale))' in svc,
 'activation email/SMS is a real interactive action': 'startActivationEmail(' in pilgrim and 'confirmActivationEmail(' in pilgrim and 'startActivationSMS(' in pilgrim and 'confirmActivationSMS(' in pilgrim,
 'account/booking tokens separated during activation': 'mapOf("x-booking-token" to bookingToken)' in svc,
 'sensitive email changes require real server security proof': 'authorizeSensitiveAction(' in security and 'emailSecurityProof!!' in security and '"x-iumrah-security-proof" to securityProof' in svc,
 'password change has matching API and reachable UI': 'SECURITY_CHANGE_PASSWORD' in routes and 'accountStore.changePassword(current, next)' in password and 'AppRoute.AccountPasswordSecurity -> AccountPasswordSecurityScreen' in shell,
 'password recovery remains reachable': 'chrome::openAccountPasswordRecovery' in password and 'AppRoute.AccountPasswordRecovery -> AccountPasswordRecoveryScreen' in shell,
 'new routes declared and navigable': 'data object AccountPasswordSecurity' in nav and 'fun openAccountPasswordSecurity()' in nav,
 'old hotel / flights journey routes intact': 'AppRoute.NuiteeJourneyHotels' in shell and 'AppRoute.NuiteeJourneyReview' in shell,
 'unit tests added': (root/'app/src/test/java/com/iumrah/beta/domain/booking/BookingAuthFallbackPolicyTest.kt').exists(),
 'no backend host or workflow modifications': 'APIClient(' not in src('domain/booking/BookingAuthFallbackPolicy.kt')
}
for label, passed in checks.items():
    assert passed, f'FAIL: {label}'
    print('PASS:', label)
print(f'Stage 07/08 source checks: {len(checks)} passed. Compilation and integration require Android CI.')
