package com.iumrah.beta.data.account

import com.iumrah.beta.models.account.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class IumrahAccountContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test fun loginPayloadMatchesSwiftContract() {
        val payload = IumrahAccountLoginRequest(
            identifier = "001234",
            password = "secret",
            device = IumrahClientDevice(
                installationID = "12345678-1234-1234-1234-123456789012",
                secret = "abcdefghijklmnopqrstuvwxyzABCDEF1234567890_-",
                name = "Samsung SM-S928B",
                model = "SM-S928B",
                platform = "Android",
                osVersion = "16 (API 36)",
                appVersion = "0.2.0-stage2 (2)",
                locale = "ru-UZ",
            ),
        )
        val node = json.parseToJsonElement(json.encodeToString(payload)).jsonObject
        assertEquals("001234", node.getValue("identifier").jsonPrimitive.content)
        val device = node.getValue("device").jsonObject
        assertTrue(device.containsKey("installationID"))
        assertTrue(device.containsKey("osVersion"))
        assertEquals("Android", device.getValue("platform").jsonPrimitive.content)
    }

    @Test fun accountProfilePreservesSwiftMissingFieldDefaults() {
        val profile = json.decodeFromString<IumrahAccountProfile>("""{"iumrahID":"000777"}""")
        assertEquals("000777", profile.iumrahID)
        assertEquals("", profile.displayName)
        assertEquals("", profile.email)
        assertEquals("", profile.whatsapp)
    }

    @Test fun securityStateLogicMatchesSwift() {
        val value = IumrahSecurityConfirmation(
            bookingID="b1", status="under_review", firstName="A", lastName="B",
            passportLast4="1234", verificationMethod="passport", reusedIdentity=false,
            hasPassportPhoto=true, reviewNote="", submittedAt="x", updatedAt="x",
        )
        assertTrue(value.isPendingReview)
        assertTrue(value.isSubmitted)
        assertFalse(value.canEdit)
        assertFalse(value.isConfirmed)
    }

    @Test fun passwordlessVerificationMatchesCurrentIOS() {
        val device = IumrahClientDevice("install", "device-secret", "Samsung", "S24", platform="Android", osVersion="16", appVersion="3", locale="ru-UZ")
        val sms = json.parseToJsonElement(json.encodeToString(IumrahPhoneRegistrationConfirmRequest("challenge", "123456", device))).jsonObject
        val email = json.parseToJsonElement(json.encodeToString(IumrahRegistrationEmailConfirmRequest("challenge", "123456", "A", "B", device))).jsonObject
        assertFalse(sms.containsKey("password"))
        assertFalse(email.containsKey("password"))
        assertEquals("123456", sms.getValue("code").jsonPrimitive.content)
        assertEquals("A", email.getValue("firstName").jsonPrimitive.content)
    }

    @Test fun loginActivationAndSecurityRoutesMatchIOS() {
        assertEquals("/api/package/client/account/login/email/start", IumrahAccountRoutes.EMAIL_LOGIN_START)
        assertEquals("/api/package/client/account/login/email/confirm", IumrahAccountRoutes.EMAIL_LOGIN_CONFIRM)
        assertEquals("/api/package/client/account/activate/sms/start", IumrahAccountRoutes.ACTIVATE_SMS_START)
        assertEquals("/api/package/client/account/activate/email/confirm", IumrahAccountRoutes.ACTIVATE_EMAIL_CONFIRM)
        assertEquals("/api/package/client/account/security/authorize/password", IumrahAccountRoutes.SECURITY_AUTHORIZE_PASSWORD)
        assertEquals("/api/package/client/account/security/password/change", IumrahAccountRoutes.SECURITY_CHANGE_PASSWORD)
        assertEquals("/api/package/client/account/public-card", IumrahAccountRoutes.PUBLIC_IDENTITY)
    }

    @Test fun routesAreExactlyTheExistingBackendRoutes() {
        assertEquals("/api/package/client/account/login", IumrahAccountRoutes.LOGIN)
        assertEquals("/api/package/client/account/security/register", IumrahAccountRoutes.SECURITY_REGISTER)
        assertEquals("/api/catalog/hotels/client/trips/abc/checkout", IumrahAccountRoutes.checkout("abc"))
        assertEquals("/api/package/client/account/security/sessions/s1", IumrahAccountRoutes.terminateSession("s1"))
    }
}
