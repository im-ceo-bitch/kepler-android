package com.iumrah.beta.data.account

import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.models.account.*
import com.iumrah.beta.models.booking.ClientTripSnapshot
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
private data class StoredIumrahAccountSession(val token: String, val account: IumrahAccountProfile)

data class IumrahAccountState(
    val account: IumrahAccountProfile? = null,
    val isRestoring: Boolean = false,
    val lastError: String? = null,
) {
    val isAuthenticated: Boolean get() = account != null
    val iumrahID: String? get() = account?.iumrahID
}

class IumrahAccountStore(
    private val service: IumrahAccountService,
    private val vault: SecureJsonStore,
    private val clearReferral: () -> Unit = {},
) {
    private val _state: MutableStateFlow<IumrahAccountState>
    val state: StateFlow<IumrahAccountState> get() = _state.asStateFlow()
    private var token: String? = null
    private val initialLoad = runCatching { vault.readStrict(VAULT_KEY, StoredIumrahAccountSession.serializer()) }

    init {
        val saved = initialLoad.getOrNull()
        token = saved?.token
        _state = MutableStateFlow(IumrahAccountState(account = saved?.account, lastError = initialLoad.exceptionOrNull()?.let { "Account storage unavailable" }))
    }

    val bearerToken: String? get() = token

    fun authorizationHeaders(bookingToken: String? = null): Map<String, String> = buildMap {
        token?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
        bookingToken?.trim()?.takeIf { it.isNotEmpty() }?.let { put("x-booking-token", it) }
    }

    suspend fun restore() {
        val current = token ?: return
        _state.update { it.copy(isRestoring = true) }
        try {
            val profile = service.session(current)
            if (token != current) return
            saveSession(current, profile)
            runCatching { service.registerCurrentSession(current, Locale.getDefault().toLanguageTag()) }
            if (token == current) _state.value = IumrahAccountState(account = profile, isRestoring = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (token == current) {
                // Do not delete a valid offline session because of a network or 5xx failure.
                if ((error is APIException.Status && error.code == 401) ||
                    (error is APIException.Server && error.code == 401)) clearLocalSession()
                else _state.update { it.copy(lastError = error.message) }
            }
        } finally {
            if (token == current) _state.update { it.copy(isRestoring = false) }
        }
    }

    suspend fun activate(bookingID: String, bookingToken: String, password: String, locale: String = Locale.getDefault().toLanguageTag()): IumrahAccountProfile {
        val response = service.activate(bookingID, bookingToken, password, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, Locale.getDefault().toLanguageTag()) }
        return response.account
    }

    suspend fun startEmailLogin(email: String, locale: String): IumrahEmailChallengeStartResponse =
        service.startEmailLogin(email, locale)

    suspend fun confirmEmailLogin(challengeID: String, code: String, locale: String): IumrahAccountProfile {
        val response = service.confirmEmailLogin(challengeID, code, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun startActivationEmail(bookingID: String, bookingToken: String, email: String, locale: String): IumrahEmailChallengeStartResponse =
        service.startActivationEmail(bookingID, bookingToken, email, locale)

    suspend fun confirmActivationEmail(bookingID: String, bookingToken: String, challengeID: String, code: String, locale: String): IumrahAccountProfile {
        val response = service.confirmActivationEmail(bookingID, bookingToken, challengeID, code, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun startActivationSMS(bookingID: String, bookingToken: String, phone: String, locale: String): IumrahPhoneLoginStartResponse =
        service.startActivationSMS(bookingID, bookingToken, phone, locale)

    suspend fun confirmActivationSMS(bookingID: String, bookingToken: String, challengeID: String, code: String, locale: String): IumrahAccountProfile {
        val response = service.confirmActivationSMS(bookingID, bookingToken, challengeID, code, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun login(identifier: String, password: String, locale: String = Locale.getDefault().toLanguageTag()): IumrahAccountProfile {
        val response = service.login(identifier, password, locale)
        setSession(response)
        return response.account
    }

    suspend fun startPhoneLogin(phone: String, locale: String = Locale.getDefault().toLanguageTag()): IumrahPhoneLoginStartResponse =
        service.startPhoneLogin(phone, locale)

    suspend fun confirmPhoneLogin(challengeID: String, code: String, locale: String = Locale.getDefault().toLanguageTag()): IumrahAccountProfile {
        val response = service.confirmPhoneLogin(challengeID, code, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun startPhoneRegistration(phone: String, firstName: String, lastName: String, locale: String): IumrahPhoneLoginStartResponse =
        service.startPhoneRegistration(phone, firstName, lastName, locale)

    suspend fun confirmPhoneRegistration(challengeID: String, code: String, locale: String): IumrahAccountProfile {
        val response = service.confirmPhoneRegistration(challengeID, code, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun startEmailRegistration(email: String, locale: String): IumrahEmailChallengeStartResponse =
        service.startEmailRegistration(email, locale)

    suspend fun confirmEmailRegistration(challengeID: String, code: String, firstName: String, lastName: String, locale: String): IumrahAccountProfile {
        val response = service.confirmEmailRegistration(challengeID, code, firstName, lastName, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun signInWithApple(credential: IumrahAppleCredential, locale: String): IumrahAccountProfile {
        val response = service.signInWithApple(credential, locale)
        setSession(response)
        return response.account
    }

    suspend fun updateProfile(firstName: String, lastName: String, phone: String, email: String, telegram: String, whatsapp: String): IumrahAccountProfile {
        val current = requireToken()
        val profile = service.updateProfile(IumrahAccountProfileUpdateRequest(firstName, lastName, phone, email, telegram, whatsapp), current)
        check(token == current) { "Account changed" }
        saveSession(current, profile)
        _state.value = IumrahAccountState(account = profile)
        return profile
    }

    suspend fun logout() {
        val current = token
        current?.let { service.logout(it) }
        if (token == current) clearLocalSession()
    }

    /** Account access is only deleted following a confirmed server response. */
    suspend fun deleteAccount() {
        val current = requireToken()
        service.deleteAccount(current)
        if (token == current) clearLocalSession()
    }

    suspend fun securityOverview(locale: String): IumrahSecurityOverview {
        val current = requireToken()
        return try {
            service.registerCurrentSession(current, locale)
            service.securityOverview(current).also { check(token == current) { "Account changed" } }
        } catch (error: Throwable) {
            val revoked = (error is APIException.Status && error.code == 401) ||
                (error is APIException.Server && error.code == 401)
            if (revoked && token == current) clearLocalSession()
            throw error
        }
    }

    suspend fun friendsDashboard(): IumrahFriendsDashboard {
        val current = requireToken()
        val dashboard = try { service.friendsDashboard(current) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            check(token == current) { "Account changed" }
            service.registerCurrentSession(current, Locale.getDefault().toLanguageTag())
            service.friendsDashboard(current)
        }
        check(token == current) { "Account changed" }
        return dashboard
    }

    private suspend fun <T> scopedSecurity(action: suspend (String) -> T): T {
        val current = requireToken()
        val result = action(current)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(token == current) { "Account changed" }
        return result
    }

    suspend fun claimPrimaryDevice(password: String): IumrahSecurityOverview = scopedSecurity { service.claimPrimaryDevice(password, it) }

    suspend fun terminateSecuritySession(id: String): Boolean {
        val current = requireToken()
        val response = service.terminateSession(id, current)
        check(response.ok && token == current) { "Session termination not confirmed or account changed" }
        if (response.signedOut) clearLocalSession()
        return response.signedOut
    }

    suspend fun linkApple(credential: IumrahAppleCredential): IumrahAppleLinkResponse = scopedSecurity { service.linkApple(credential, it).also { result -> check(result.ok && result.appleLinked) { "Apple link not confirmed" } } }

    suspend fun signInWithGoogle(credential: IumrahGoogleCredential, locale: String = Locale.getDefault().toLanguageTag()): IumrahAccountProfile {
        val response = service.signInWithGoogle(credential, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun linkGoogle(credential: IumrahGoogleCredential): IumrahGoogleLinkResponse = scopedSecurity { service.linkGoogle(credential, it).also { result -> check(result.ok && result.googleLinked) { "Google link not confirmed" } } }

    suspend fun authorizeSensitiveAction(password: String): IumrahSensitiveAuthorizationResponse =
        scopedSecurity { service.authorizeSensitiveAction(password, it).also { result -> check(result.ok && result.securityProof.isNotBlank()) { "Authorization not confirmed" } } }

    suspend fun authorizeSensitiveActionWithGoogle(credential: IumrahGoogleCredential): IumrahSensitiveAuthorizationResponse =
        scopedSecurity { service.authorizeSensitiveActionWithGoogle(credential, it).also { result -> check(result.ok && result.securityProof.isNotBlank()) { "Authorization not confirmed" } } }

    suspend fun authorizeSensitiveActionWithApple(credential: IumrahAppleCredential): IumrahSensitiveAuthorizationResponse =
        scopedSecurity { service.authorizeSensitiveActionWithApple(credential, it).also { result -> check(result.ok && result.securityProof.isNotBlank()) { "Authorization not confirmed" } } }

    suspend fun startPrimaryRecovery(method: String, locale: String): IumrahPrimaryRecoveryStartResponse {
        val current = requireToken()
        val result = service.startPrimaryRecovery(method, locale, current)
        check(token == current && result.ok && result.method == method && result.challengeID.isNotBlank()) { "Recovery challenge not confirmed" }
        return result
    }

    suspend fun confirmPrimaryRecovery(method: String, challengeID: String, code: String): IumrahSensitiveAuthorizationResponse {
        val current = requireToken()
        val result = service.confirmPrimaryRecovery(method, challengeID, code, current)
        check(token == current && result.ok && result.securityProof.isNotBlank()) { "Recovery authorization not confirmed" }
        return result
    }

    suspend fun unlinkApple() {
        val current = requireToken()
        check(service.unlinkApple(current).ok && token == current) { "Apple unlink not confirmed" }
    }

    suspend fun unlinkGoogle() {
        val current = requireToken()
        check(service.unlinkGoogle(current).ok && token == current) { "Google unlink not confirmed" }
    }

    suspend fun startPhoneVerification(phone: String, locale: String, securityProof: String): IumrahPhoneChallengeStartResponse {
        val current = requireToken()
        val result = service.startPhoneVerification(phone, locale, securityProof, current)
        check(token == current && result.ok && result.challengeID.isNotBlank()) { "Phone challenge not confirmed" }
        return result
    }

    suspend fun confirmPhoneVerification(challengeID: String, code: String, securityProof: String): IumrahPhoneVerificationResponse {
        val current = requireToken()
        val result = service.confirmPhoneVerification(challengeID, code, securityProof, current)
        check(token == current && result.ok && result.phone.isNotBlank()) { "Phone verification not confirmed or account changed" }
        _state.value.account?.let { profile ->
            val updated = profile.copy(phone = result.phone)
            saveSession(current, updated)
            _state.value = IumrahAccountState(account = updated)
        }
        return result
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): IumrahPasswordChangeResponse =
        scopedSecurity { service.changePassword(currentPassword, newPassword, it) }

    suspend fun publicIdentityLink(): IumrahPublicIdentityLinkResponse = scopedSecurity { service.publicIdentityLink(it) }

    suspend fun walletPass(): ByteArray = scopedSecurity { service.walletPass(it) }

    suspend fun startEmailVerification(email: String, locale: String, securityProof: String): IumrahEmailChallengeStartResponse =
        scopedSecurity { service.startEmailVerification(email, locale, securityProof, it) }

    suspend fun confirmEmailVerification(challengeID: String, code: String, securityProof: String): IumrahEmailChallengeConfirmResponse {
        val current = requireToken()
        val response = service.confirmEmailVerification(challengeID, code, securityProof, current)
        check(response.ok && token == current) { "Email verification not confirmed or account changed" }
        val profile = _state.value.account
        if (profile != null) {
            val updated = profile.copy(email = response.email)
            saveSession(current, updated)
            _state.value = IumrahAccountState(account = updated)
        }
        return response
    }

    suspend fun startPasswordRecovery(email: String, locale: String): IumrahEmailChallengeStartResponse =
        service.startPasswordRecovery(email, locale)

    suspend fun confirmPasswordRecovery(challengeID: String, code: String, newPassword: String): IumrahPasswordRecoveryResponse =
        service.confirmPasswordRecovery(challengeID, code, newPassword)

    suspend fun linkBooking(bookingID: String, bookingToken: String): IumrahAccountLinkBookingResponse =
        service.linkBooking(bookingID, bookingToken, requireToken())

    suspend fun accountTrips(expectedToken: String = requireToken()): List<ClientTripSnapshot> {
        check(requireToken() == expectedToken) { "Account changed" }
        return service.trips(expectedToken)
    }

    suspend fun tripDetail(bookingID: String, expectedToken: String = requireToken()): IumrahAccountTripDetailResponse {
        check(requireToken() == expectedToken) { "Account changed" }
        return service.tripDetail(bookingID, expectedToken)
    }

    suspend fun linkBookingForAccount(bookingID: String, bookingToken: String, expectedToken: String): IumrahAccountLinkBookingResponse {
        check(requireToken() == expectedToken) { "Account changed" }
        return service.linkBooking(bookingID, bookingToken, expectedToken)
    }

    /** Accept only a real, verified server session; never replace an account changed during OTP. */
    fun acceptVerifiedSession(response: IumrahAccountAuthResponse, expectedToken: String?) {
        check(token == expectedToken && response.ok && response.session.token.isNotBlank()) { "Account changed or invalid session" }
        setSession(response)
    }

    private fun setSession(response: IumrahAccountAuthResponse) {
        check(response.ok && response.session.token.isNotBlank()) { "Invalid account session" }
        saveSession(response.session.token, response.account)
        token = response.session.token
        clearReferral()
        _state.value = IumrahAccountState(account = response.account)
    }

    private fun saveSession(token: String, profile: IumrahAccountProfile) {
        check(initialLoad.isSuccess) { "Account storage unavailable" }
        vault.write(VAULT_KEY, StoredIumrahAccountSession(token, profile), StoredIumrahAccountSession.serializer())
    }

    private fun clearLocalSession() {
        check(initialLoad.isSuccess) { "Account storage unavailable" }
        vault.remove(VAULT_KEY)
        token = null
        _state.value = IumrahAccountState()
    }

    private fun requireToken(): String = token ?: throw APIException.Status(401)

    companion object { private const val VAULT_KEY = "iumrah-account-session" }
}
