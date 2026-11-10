package com.iumrah.beta.data.account

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.security.IumrahAccountDeviceIdentity
import com.iumrah.beta.models.account.*
import com.iumrah.beta.models.booking.ClientTripSnapshot
import kotlinx.serialization.Serializable

class IumrahAccountService(
    private val api: APIClient,
    private val deviceIdentity: IumrahAccountDeviceIdentity,
) {
    suspend fun activate(bookingID: String, bookingToken: String, password: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.ACTIVATE, IumrahAccountActivateRequest(bookingID, password, deviceIdentity.current(locale)), mapOf("x-booking-token" to bookingToken))

    suspend fun login(identifier: String, password: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.LOGIN, IumrahAccountLoginRequest(identifier, password, deviceIdentity.current(locale)))

    suspend fun startEmailLogin(email: String, locale: String): IumrahEmailChallengeStartResponse =
        api.post(IumrahAccountRoutes.EMAIL_LOGIN_START, IumrahEmailLoginStartRequest(email, locale))

    suspend fun confirmEmailLogin(challengeID: String, code: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.EMAIL_LOGIN_CONFIRM, IumrahEmailLoginConfirmRequest(challengeID, code, deviceIdentity.current(locale)))

    suspend fun startActivationEmail(bookingID: String, bookingToken: String, email: String, locale: String): IumrahEmailChallengeStartResponse =
        api.post(IumrahAccountRoutes.ACTIVATE_EMAIL_START, IumrahAccountActivationEmailStartRequest(bookingID, email, locale), mapOf("x-booking-token" to bookingToken))

    suspend fun confirmActivationEmail(bookingID: String, bookingToken: String, challengeID: String, code: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.ACTIVATE_EMAIL_CONFIRM, IumrahAccountActivationEmailConfirmRequest(bookingID, challengeID, code, deviceIdentity.current(locale)), mapOf("x-booking-token" to bookingToken))

    suspend fun startActivationSMS(bookingID: String, bookingToken: String, phone: String, locale: String): IumrahPhoneLoginStartResponse =
        api.post(IumrahAccountRoutes.ACTIVATE_SMS_START, IumrahAccountActivationSMSStartRequest(bookingID, phone, locale), mapOf("x-booking-token" to bookingToken))

    suspend fun confirmActivationSMS(bookingID: String, bookingToken: String, challengeID: String, code: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.ACTIVATE_SMS_CONFIRM, IumrahAccountActivationSMSConfirmRequest(bookingID, challengeID, code, deviceIdentity.current(locale)), mapOf("x-booking-token" to bookingToken))

    suspend fun startPhoneLogin(phone: String, locale: String): IumrahPhoneLoginStartResponse =
        api.post(IumrahAccountRoutes.PHONE_LOGIN_START, IumrahPhoneLoginStartRequest(phone, locale))

    suspend fun confirmPhoneLogin(challengeID: String, code: String, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.PHONE_LOGIN_CONFIRM, IumrahPhoneLoginConfirmRequest(challengeID, code, deviceIdentity.current(locale)))

    suspend fun startPhoneRegistration(phone: String, firstName: String, lastName: String, locale: String): IumrahPhoneLoginStartResponse =
        api.post(IumrahAccountRoutes.REGISTER_SMS_START, IumrahPhoneRegistrationStartRequest(phone, firstName, lastName, locale))

    suspend fun confirmPhoneRegistration(challengeID: String, code: String, locale: String): IumrahAccountAuthResponse =
        api.post(
            IumrahAccountRoutes.REGISTER_SMS_CONFIRM,
            IumrahPhoneRegistrationConfirmRequest(challengeID, code, deviceIdentity.current(locale)),
        )

    suspend fun startEmailRegistration(email: String, locale: String): IumrahEmailChallengeStartResponse =
        api.post(IumrahAccountRoutes.REGISTER_EMAIL_START, IumrahEmailChallengeStartRequest(email, locale))

    suspend fun confirmEmailRegistration(challengeID: String, code: String, firstName: String, lastName: String, locale: String): IumrahAccountAuthResponse =
        api.post(
            IumrahAccountRoutes.REGISTER_EMAIL_CONFIRM,
            IumrahRegistrationEmailConfirmRequest(challengeID, code, firstName, lastName, deviceIdentity.current(locale)),
        )

    suspend fun session(token: String): IumrahAccountProfile {
        val value: IumrahAccountSessionResponse = api.get(IumrahAccountRoutes.SESSION, headers = bearer(token))
        return value.account
    }

    suspend fun logout(token: String) {
        runCatching { api.post<IumrahSimpleResponse, EmptyBody>(IumrahAccountRoutes.LOGOUT, EmptyBody(), bearer(token)) }
    }

    suspend fun updateProfile(request: IumrahAccountProfileUpdateRequest, token: String): IumrahAccountProfile {
        val value: IumrahAccountSessionResponse = api.put(IumrahAccountRoutes.PROFILE, request, bearer(token))
        return value.account
    }

    suspend fun trips(token: String): List<ClientTripSnapshot> {
        val value: IumrahAccountTripsResponse = api.get(IumrahAccountRoutes.TRIPS, headers = bearer(token))
        return value.trips
    }

    suspend fun tripDetail(bookingID: String, token: String): IumrahAccountTripDetailResponse =
        api.get(IumrahAccountRoutes.tripDetail(bookingID), headers = bearer(token))

    suspend fun linkBooking(bookingID: String, bookingToken: String, token: String): IumrahAccountLinkBookingResponse =
        api.post(IumrahAccountRoutes.LINK_BOOKING, IumrahAccountLinkBookingRequest(bookingID), bearer(token) + ("x-booking-token" to bookingToken))

    suspend fun checkout(bookingID: String, authorizationHeaders: Map<String, String>): IumrahCheckoutResponse =
        api.get(IumrahAccountRoutes.checkout(bookingID), headers = authorizationHeaders)

    /** iOS parity: booking proof wins; permanent account is only the auth/not-found fallback. */
    suspend fun checkoutWithFallback(bookingID: String, bookingToken: String?, accountToken: String?): IumrahCheckoutResponse {
        val bookingProof = bookingToken?.trim().orEmpty()
        val bearerToken = accountToken?.trim().orEmpty()
        if (bookingProof.isNotEmpty()) {
            try {
                return checkout(bookingID, mapOf("x-booking-token" to bookingProof))
            } catch (error: Throwable) {
                if (bearerToken.isEmpty() || !com.iumrah.beta.domain.booking.BookingAuthFallbackPolicy.mayRetryWithAccount(error)) throw error
            }
        }
        if (bearerToken.isNotEmpty()) return checkout(bookingID, mapOf("Authorization" to "Bearer $bearerToken"))
        throw com.iumrah.beta.core.network.APIException.MissingBookingToken
    }

    suspend fun saveTraveler(bookingID: String, position: Int, form: IumrahTravelerForm, token: String): IumrahTravelerForm {
        val value: IumrahTravelerSaveResponse = api.put(IumrahAccountRoutes.traveler(bookingID, position), IumrahTravelerSaveRequest(form), bearer(token))
        return value.traveler
    }

    suspend fun uploadPassport(bookingID: String, position: Int, data: ByteArray, contentType: String, token: String) {
        api.upload<IumrahSimpleResponse>(IumrahAccountRoutes.travelerPassport(bookingID, position), data, contentType, bearer(token), 60)
    }

    suspend fun uploadReceipt(bookingID: String, method: String, data: ByteArray, contentType: String, token: String): String {
        val value: IumrahReceiptResponse = api.upload(IumrahAccountRoutes.receipt(bookingID), data, contentType, bearer(token) + ("x-payment-method" to method), 60)
        return value.id
    }

    suspend fun media(path: String, token: String): ByteArray = api.fetchData(path, bearer(token))

    suspend fun registerCurrentSession(token: String, locale: String): IumrahSecurityOverview =
        api.post(IumrahAccountRoutes.SECURITY_REGISTER, IumrahDeviceRegistrationRequest(deviceIdentity.current(locale)), bearer(token))

    suspend fun securityOverview(token: String): IumrahSecurityOverview =
        api.get(IumrahAccountRoutes.SECURITY, headers = deviceIdentity.securityHeaders(token))

    suspend fun friendsDashboard(token: String): IumrahFriendsDashboard =
        api.get(IumrahAccountRoutes.FRIENDS, headers = deviceIdentity.securityHeaders(token))

    suspend fun claimPrimaryDevice(password: String, token: String): IumrahSecurityOverview =
        api.post(IumrahAccountRoutes.SECURITY_CLAIM_PRIMARY, IumrahClaimPrimaryRequest(password), deviceIdentity.securityHeaders(token))

    suspend fun terminateSession(id: String, token: String): IumrahTerminateSessionResponse =
        api.delete(IumrahAccountRoutes.terminateSession(id), deviceIdentity.securityHeaders(token))

    suspend fun signInWithApple(credential: IumrahAppleCredential, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.APPLE_SIGN_IN, IumrahAppleSignInRequest(credential.identityToken, credential.nonce, deviceIdentity.current(locale)))

    suspend fun linkApple(credential: IumrahAppleCredential, token: String): IumrahAppleLinkResponse =
        api.post(IumrahAccountRoutes.APPLE_LINK, IumrahAppleRequest(credential.identityToken, credential.nonce), deviceIdentity.securityHeaders(token))

    suspend fun signInWithGoogle(credential: IumrahGoogleCredential, locale: String): IumrahAccountAuthResponse =
        api.post(IumrahAccountRoutes.GOOGLE_SIGN_IN, IumrahGoogleSignInRequest(credential.identityToken, credential.nonce, deviceIdentity.current(locale)))

    suspend fun linkGoogle(credential: IumrahGoogleCredential, token: String): IumrahGoogleLinkResponse =
        api.post(IumrahAccountRoutes.GOOGLE_LINK, IumrahGoogleRequest(credential.identityToken, credential.nonce), deviceIdentity.securityHeaders(token))

    suspend fun authorizeSensitiveAction(password: String, token: String): IumrahSensitiveAuthorizationResponse =
        api.post(IumrahAccountRoutes.SECURITY_AUTHORIZE_PASSWORD, IumrahClaimPrimaryRequest(password), deviceIdentity.securityHeaders(token))

    suspend fun authorizeSensitiveActionWithGoogle(credential: IumrahGoogleCredential, token: String): IumrahSensitiveAuthorizationResponse =
        api.post(IumrahAccountRoutes.SECURITY_AUTHORIZE_GOOGLE, IumrahGoogleRequest(credential.identityToken, credential.nonce), deviceIdentity.securityHeaders(token))

    suspend fun changePassword(currentPassword: String, newPassword: String, token: String): IumrahPasswordChangeResponse =
        api.post(IumrahAccountRoutes.SECURITY_CHANGE_PASSWORD, IumrahPasswordChangeRequest(currentPassword, newPassword), deviceIdentity.securityHeaders(token))

    suspend fun startEmailVerification(email: String, locale: String, securityProof: String, token: String): IumrahEmailChallengeStartResponse =
        api.post(IumrahAccountRoutes.EMAIL_START, IumrahEmailChallengeStartRequest(email, locale), deviceIdentity.securityHeaders(token) + ("x-iumrah-security-proof" to securityProof))

    suspend fun confirmEmailVerification(challengeID: String, code: String, securityProof: String, token: String): IumrahEmailChallengeConfirmResponse =
        api.post(IumrahAccountRoutes.EMAIL_CONFIRM, IumrahEmailChallengeConfirmRequest(challengeID, code), deviceIdentity.securityHeaders(token) + ("x-iumrah-security-proof" to securityProof))

    suspend fun publicIdentityLink(token: String): IumrahPublicIdentityLinkResponse =
        api.get(IumrahAccountRoutes.PUBLIC_IDENTITY, headers = bearer(token))

    suspend fun walletPass(token: String): ByteArray = api.fetchData(IumrahAccountRoutes.WALLET_PASS, bearer(token))

    suspend fun startPasswordRecovery(email: String, locale: String): IumrahEmailChallengeStartResponse =
        api.post(IumrahAccountRoutes.PASSWORD_RECOVERY_START, IumrahEmailChallengeStartRequest(email, locale))

    suspend fun confirmPasswordRecovery(challengeID: String, code: String, newPassword: String): IumrahPasswordRecoveryResponse =
        api.post(IumrahAccountRoutes.PASSWORD_RECOVERY_CONFIRM, IumrahPasswordRecoveryConfirmRequest(challengeID, code, newPassword))

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token")
}

@Serializable
private class EmptyBody
