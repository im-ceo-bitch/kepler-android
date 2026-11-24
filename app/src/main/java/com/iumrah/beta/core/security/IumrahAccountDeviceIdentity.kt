package com.iumrah.beta.core.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.iumrah.beta.models.account.IumrahClientDevice
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
private data class DeviceCredentials(val installationID: String, val secret: String)

class IumrahAccountDeviceIdentity(
    private val context: Context,
    private val store: SecureJsonStore,
) {
    @Synchronized
    fun current(locale: String = Locale.getDefault().toLanguageTag()): IumrahClientDevice {
        val credentials = store.readStrict(CREDENTIALS_KEY, DeviceCredentials.serializer()) ?: createCredentials()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = info.versionName.orEmpty()
        val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toString()
        }
        val appVersion = if (buildNumber == "0") version else if (version.isBlank()) buildNumber else "$version ($buildNumber)"
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim().ifBlank { "Android" }
        val hardwareIdentifier = listOf(Build.BRAND, Build.DEVICE)
            .map { it.orEmpty().trim() }
            .filter { it.isNotBlank() }
            .joinToString("/")
            .ifBlank { model }
        val systemDeviceName = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull().orEmpty().trim()
        val emulator = Build.FINGERPRINT.startsWith("generic", true) ||
            Build.FINGERPRINT.contains("emulator", true) ||
            Build.MODEL.contains("sdk", true) ||
            Build.MODEL.contains("emulator", true) ||
            Build.MODEL.contains("gphone", true)
        val fallbackName = listOf(manufacturer.takeIf { !model.startsWith(it, true) }, model)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
        val deviceName = when {
            emulator -> if (model.isBlank()) "Android Emulator" else "$model Emulator"
            systemDeviceName.isNotBlank() && !systemDeviceName.equals("Android", true) -> systemDeviceName
            else -> fallbackName
        }.take(120)

        return IumrahClientDevice(
            installationID = credentials.installationID,
            secret = credentials.secret,
            name = deviceName,
            model = model,
            hardwareIdentifier = hardwareIdentifier,
            platform = "android",
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = appVersion,
            locale = locale.take(24),
        )
    }

    fun securityHeaders(token: String): Map<String, String> {
        val device = current()
        return mapOf(
            "Authorization" to "Bearer $token",
            "x-iumrah-device-id" to device.installationID,
            "x-iumrah-device-secret" to device.secret,
        )
    }

    private fun createCredentials(): DeviceCredentials {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val value = DeviceCredentials(
            installationID = UUID.randomUUID().toString().lowercase(Locale.ROOT),
            secret = secret,
        )
        store.write(CREDENTIALS_KEY, value, DeviceCredentials.serializer())
        return value
    }

    companion object {
        private const val CREDENTIALS_KEY = "account-device-installation"
    }
}
