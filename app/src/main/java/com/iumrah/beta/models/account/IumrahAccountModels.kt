package com.iumrah.beta.models.account

import com.iumrah.beta.models.booking.ClientBookingAssignment
import com.iumrah.beta.models.booking.ClientESIMProfile
import com.iumrah.beta.models.booking.ClientTripSnapshot
import com.iumrah.beta.models.booking.BookingStatusHistoryEntry
import com.iumrah.beta.models.booking.RemoteBooking
import kotlinx.serialization.Serializable

@Serializable
data class IumrahAccountProfile(
    val iumrahID: String,
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val telegram: String = "",
    val whatsapp: String = "",
)

@Serializable
data class IumrahAccountProfileUpdateRequest(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val telegram: String,
    val whatsapp: String,
)

@Serializable
data class IumrahAccountSession(val token: String, val expiresAt: String)

@Serializable
data class IumrahAccountAuthResponse(
    val ok: Boolean,
    val account: IumrahAccountProfile,
    val session: IumrahAccountSession,
)

@Serializable
data class IumrahAccountSessionResponse(val ok: Boolean, val account: IumrahAccountProfile)

@Serializable
data class IumrahClientDevice(
    val installationID: String,
    val secret: String,
    val name: String,
    val model: String,
    val hardwareIdentifier: String = "",
    val platform: String,
    val osVersion: String,
    val appVersion: String,
    val locale: String,
)

@Serializable
data class IumrahAccountLoginRequest(val identifier: String, val password: String, val device: IumrahClientDevice)

@Serializable data class IumrahEmailLoginStartRequest(val email: String, val locale: String)
@Serializable data class IumrahEmailLoginConfirmRequest(val challengeID: String, val code: String, val device: IumrahClientDevice)
@Serializable data class IumrahAccountActivationEmailStartRequest(val bookingID: String, val email: String, val locale: String)
@Serializable data class IumrahAccountActivationEmailConfirmRequest(val bookingID: String, val challengeID: String, val code: String, val device: IumrahClientDevice)
@Serializable data class IumrahAccountActivationSMSStartRequest(val bookingID: String, val phone: String, val locale: String)
@Serializable data class IumrahAccountActivationSMSConfirmRequest(val bookingID: String, val challengeID: String, val code: String, val device: IumrahClientDevice)
@Serializable data class IumrahSensitiveAuthorizationResponse(val ok: Boolean, val securityProof: String, val proofExpiresAt: String, val overview: IumrahSecurityOverview)
@Serializable data class IumrahPasswordChangeRequest(val currentPassword: String, val newPassword: String)
@Serializable data class IumrahPasswordChangeResponse(val ok: Boolean, val sessionsRevoked: Boolean)
@Serializable data class IumrahPublicIdentityLinkResponse(val ok: Boolean, val iumrahID: String, val url: String)

@Serializable data class IumrahPhoneLoginStartRequest(val phone: String, val locale: String)
@Serializable data class IumrahPhoneLoginStartResponse(val ok: Boolean, val challengeID: String, val expiresAt: String? = null, val debugCode: String? = null)
@Serializable data class IumrahPhoneLoginConfirmRequest(val challengeID: String, val code: String, val device: IumrahClientDevice)
@Serializable data class IumrahPhoneRegistrationStartRequest(
    val phone: String,
    val firstName: String,
    val lastName: String,
    val locale: String,
)
@Serializable data class IumrahPhoneRegistrationConfirmRequest(
    val challengeID: String,
    val code: String,
    val device: IumrahClientDevice,
)
@Serializable data class IumrahRegistrationEmailConfirmRequest(
    val challengeID: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    val device: IumrahClientDevice,
)

@Serializable
data class IumrahAccountActivateRequest(val bookingID: String, val password: String, val device: IumrahClientDevice)

@Serializable
data class IumrahAccountTripsResponse(val ok: Boolean, val trips: List<ClientTripSnapshot>)

@Serializable
data class IumrahTravelerForm(
    val position: Int,
    val travelerType: String,
    val relationship: String? = null,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val gender: String,
    val dateOfBirth: String,
    val placeOfBirth: String,
    val nationality: String,
    val residenceCountry: String,
    val passportNumber: String,
    val passportIssueDate: String,
    val passportExpiryDate: String,
    val passportIssuingCountry: String,
    val phone: String,
    val email: String,
    val emergencyName: String,
    val emergencyPhone: String,
    val emergencyRelation: String,
    val hasPassport: Boolean,
    val completed: Boolean,
) { val id: Int get() = position }

@Serializable
data class IumrahTravelerSaveRequest(
    val relationship: String? = null,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val gender: String,
    val dateOfBirth: String,
    val placeOfBirth: String,
    val nationality: String,
    val residenceCountry: String,
    val passportNumber: String,
    val passportIssueDate: String,
    val passportExpiryDate: String,
    val passportIssuingCountry: String,
    val phone: String,
    val email: String,
    val emergencyName: String,
    val emergencyPhone: String,
    val emergencyRelation: String,
) {
    constructor(form: IumrahTravelerForm) : this(
        relationship = form.relationship,
        firstName = form.firstName,
        middleName = form.middleName,
        lastName = form.lastName,
        gender = form.gender,
        dateOfBirth = form.dateOfBirth,
        placeOfBirth = form.placeOfBirth,
        nationality = form.nationality,
        residenceCountry = form.residenceCountry,
        passportNumber = form.passportNumber,
        passportIssueDate = form.passportIssueDate,
        passportExpiryDate = form.passportExpiryDate,
        passportIssuingCountry = form.passportIssuingCountry,
        phone = form.phone,
        email = form.email,
        emergencyName = form.emergencyName,
        emergencyPhone = form.emergencyPhone,
        emergencyRelation = form.emergencyRelation,
    )
}

@Serializable
data class IumrahTravelerSaveResponse(val ok: Boolean, val traveler: IumrahTravelerForm)

@Serializable
data class IumrahCheckoutPayment(
    val visaCardNumber: String,
    val visaHolder: String,
    val hasPaymeQR: Boolean,
    val paymeQRURL: String? = null,
    val humoCardNumber: String,
    val humoHolder: String,
    val instructions: String,
)

@Serializable
data class IumrahPaymentReceipt(
    val id: String,
    val paymentMethod: String,
    val note: String? = null,
    val reviewStatus: String,
    val createdAt: String,
)

@Serializable
data class IumrahTravelDocument(
    val id: String,
    val documentKind: String,
    val title: String,
    val contentType: String,
    val createdAt: String,
    val url: String,
)

@Serializable
data class IumrahCheckoutResponse(
    val ok: Boolean,
    val iumrahID: String,
    val accountActive: Boolean,
    val status: String,
    val travelers: List<IumrahTravelerForm>,
    val payment: IumrahCheckoutPayment,
    val receipts: List<IumrahPaymentReceipt>,
    val documents: List<IumrahTravelDocument>,
)

@Serializable data class IumrahSimpleResponse(val ok: Boolean)
@Serializable data class IumrahReceiptResponse(val ok: Boolean, val id: String)
@Serializable data class IumrahDeviceRegistrationRequest(val device: IumrahClientDevice)

@Serializable
data class IumrahSecuritySession(
    val id: String,
    val deviceName: String,
    val model: String,
    val hardwareIdentifier: String = "",
    val platform: String,
    val osVersion: String,
    val appVersion: String,
    val city: String,
    val region: String,
    val country: String,
    val createdAt: String,
    val lastActiveAt: String,
    val expiresAt: String,
    val isCurrent: Boolean,
    val isPrimary: Boolean,
    val canTerminate: Boolean,
)

@Serializable data class IumrahAppleConnectionStatus(val linked: Boolean, val linkedAt: String? = null)
@Serializable data class IumrahGoogleConnectionStatus(val linked: Boolean, val linkedAt: String? = null)
@Serializable data class IumrahVerifiedLoginEmail(val email: String, val verifiedAt: String)

@Serializable
data class IumrahSecurityOverview(
    val ok: Boolean,
    val iumrahID: String,
    val currentSessionID: String,
    val currentDeviceIsPrimary: Boolean,
    val primaryDeviceProtected: Boolean,
    val loginEmail: IumrahVerifiedLoginEmail? = null,
    val apple: IumrahAppleConnectionStatus,
    val google: IumrahGoogleConnectionStatus? = null,
    val sessions: List<IumrahSecuritySession>,
)

@Serializable data class IumrahClaimPrimaryRequest(val password: String)
@Serializable data class IumrahAppleRequest(val identityToken: String, val nonce: String)
@Serializable data class IumrahAppleSignInRequest(val identityToken: String, val nonce: String, val device: IumrahClientDevice)
@Serializable data class IumrahAppleLinkResponse(val ok: Boolean, val appleLinked: Boolean, val iumrahID: String)
@Serializable data class IumrahGoogleRequest(val identityToken: String, val nonce: String)
@Serializable data class IumrahGoogleSignInRequest(val identityToken: String, val nonce: String, val device: IumrahClientDevice)
@Serializable data class IumrahGoogleLinkResponse(val ok: Boolean, val googleLinked: Boolean, val iumrahID: String)
@Serializable data class IumrahEmailChallengeStartRequest(val email: String, val locale: String)
@Serializable data class IumrahEmailChallengeStartResponse(val ok: Boolean, val challengeID: String, val expiresAt: String? = null)
@Serializable data class IumrahEmailChallengeConfirmRequest(val challengeID: String, val code: String)
@Serializable data class IumrahEmailChallengeConfirmResponse(val ok: Boolean, val email: String, val verifiedAt: String)
@Serializable data class IumrahPasswordRecoveryConfirmRequest(val challengeID: String, val code: String, val newPassword: String)
@Serializable data class IumrahPasswordRecoveryResponse(val ok: Boolean, val iumrahID: String, val sessionsRevoked: Boolean)
@Serializable data class IumrahTerminateSessionResponse(val ok: Boolean, val signedOut: Boolean)

@Serializable
data class IumrahAccountTripDetailResponse(
    val ok: Boolean,
    val trip: ClientTripSnapshot,
    val booking: RemoteBooking,
    val assignment: ClientBookingAssignment? = null,
    val esims: List<ClientESIMProfile>? = null,
    val statusHistory: List<BookingStatusHistoryEntry>? = null,
)

@Serializable data class IumrahAccountLinkBookingRequest(val bookingID: String)
@Serializable data class IumrahAccountLinkBookingResponse(val ok: Boolean, val pilgrimID: String, val bookingNumber: Int? = null, val bookingDisplayNumber: String? = null)

@Serializable
data class IumrahAppleCredential(val identityToken: String, val nonce: String)

data class IumrahGoogleCredential(val identityToken: String, val nonce: String)
