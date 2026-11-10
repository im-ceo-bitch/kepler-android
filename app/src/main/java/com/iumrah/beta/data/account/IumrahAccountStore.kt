package com.iumrah.beta.data.account

import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.models.account.*
import com.iumrah.beta.models.booking.ClientTripSnapshot
import java.util.Locale
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
) {
    private val _state: MutableStateFlow<IumrahAccountState>
    val state: StateFlow<IumrahAccountState> get() = _state.asStateFlow()
    private var token: String? = null

    init {
        val saved = vault.read(VAULT_KEY, StoredIumrahAccountSession.serializer())
        token = saved?.token
        _state = MutableStateFlow(IumrahAccountState(account = saved?.account))
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
            saveSession(current, profile)
            runCatching { service.registerCurrentSession(current, Locale.getDefault().toLanguageTag()) }
            _state.value = IumrahAccountState(account = profile, isRestoring = false)
        } catch (_: Throwable) {
            clearLocalSession()
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
        saveSession(current, profile)
        _state.value = IumrahAccountState(account = profile)
        return profile
    }

    suspend fun logout() {
        token?.let { service.logout(it) }
        clearLocalSession()
    }

    suspend fun securityOverview(locale: String): IumrahSecurityOverview {
        val current = requireToken()
        return try {
            service.registerCurrentSession(current, locale)
            service.securityOverview(current)
        } catch (error: Throwable) {
            val revoked = (error is APIException.Status && error.code == 401) ||
                (error is APIException.Server && error.code == 401)
            if (revoked) clearLocalSession()
            throw error
        }
    }

    suspend fun friendsDashboard(): IumrahFriendsDashboard {
        val current = requireToken()
        runCatching { service.registerCurrentSession(current, Locale.getDefault().toLanguageTag()) }
        return service.friendsDashboard(current)
    }

    suspend fun claimPrimaryDevice(password: String): IumrahSecurityOverview = service.claimPrimaryDevice(password, requireToken())

    suspend fun terminateSecuritySession(id: String): Boolean {
        val response = service.terminateSession(id, requireToken())
        if (response.signedOut) clearLocalSession()
        return response.signedOut
    }

    suspend fun linkApple(credential: IumrahAppleCredential): IumrahAppleLinkResponse = service.linkApple(credential, requireToken())

    suspend fun signInWithGoogle(credential: IumrahGoogleCredential, locale: String = Locale.getDefault().toLanguageTag()): IumrahAccountProfile {
        val response = service.signInWithGoogle(credential, locale)
        setSession(response)
        runCatching { service.registerCurrentSession(response.session.token, locale) }
        return response.account
    }

    suspend fun linkGoogle(credential: IumrahGoogleCredential): IumrahGoogleLinkResponse = service.linkGoogle(credential, requireToken())

    suspend fun authorizeSensitiveAction(password: String): IumrahSensitiveAuthorizationResponse =
        service.authorizeSensitiveAction(password, requireToken())

    suspend fun authorizeSensitiveActionWithGoogle(credential: IumrahGoogleCredential): IumrahSensitiveAuthorizationResponse =
        service.authorizeSensitiveActionWithGoogle(credential, requireToken())

    suspend fun changePassword(currentPassword: String, newPassword: String): IumrahPasswordChangeResponse =
        service.changePassword(currentPassword, newPassword, requireToken())

    suspend fun publicIdentityLink(): IumrahPublicIdentityLinkResponse = service.publicIdentityLink(requireToken())

    suspend fun walletPass(): ByteArray = service.walletPass(requireToken())

    suspend fun startEmailVerification(email: String, locale: String, securityProof: String): IumrahEmailChallengeStartResponse =
        service.startEmailVerification(email, locale, securityProof, requireToken())

    suspend fun confirmEmailVerification(challengeID: String, code: String, securityProof: String): IumrahEmailChallengeConfirmResponse {
        val current = requireToken()
        val response = service.confirmEmailVerification(challengeID, code, securityProof, current)
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

    suspend fun accountTrips(): List<ClientTripSnapshot> = service.trips(requireToken())

    suspend fun tripDetail(bookingID: String): IumrahAccountTripDetailResponse = service.tripDetail(bookingID, requireToken())

    private fun setSession(response: IumrahAccountAuthResponse) {
        token = response.session.token
        saveSession(response.session.token, response.account)
        _state.value = IumrahAccountState(account = response.account)
    }

    private fun saveSession(token: String, profile: IumrahAccountProfile) {
        vault.write(VAULT_KEY, StoredIumrahAccountSession(token, profile), StoredIumrahAccountSession.serializer())
    }

    private fun clearLocalSession() {
        token = null
        vault.remove(VAULT_KEY)
        _state.value = IumrahAccountState()
    }

    private fun requireToken(): String = token ?: throw APIException.Status(401)

    companion object { private const val VAULT_KEY = "iumrah-account-session" }
}
