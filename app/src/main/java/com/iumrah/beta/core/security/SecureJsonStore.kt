package com.iumrah.beta.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class SecureJsonStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Synchronized
    fun <T> read(key: String, serializer: KSerializer<T>): T? {
        return try {
            val encoded = prefs.getString(key, null) ?: return null
            val raw = decrypt(encoded)
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            // A transient Keystore or decoding error is not an instruction to erase
            // the only persisted booking/access proof. Keep the ciphertext intact.
            null
        }
    }

    @Synchronized
    fun <T> write(key: String, value: T, serializer: KSerializer<T>) {
        val raw = json.encodeToString(serializer, value)
        prefs.edit().putString(key, encrypt(raw)).commit()
    }

    @Synchronized
    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    private fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        return b64(iv) + "." + b64(cipherText)
    }

    private fun decrypt(encoded: String): String {
        val pieces = encoded.split('.', limit = 2)
        require(pieces.size == 2) { "Invalid secure payload" }
        val iv = fromB64(pieces[0])
        val cipherText = fromB64(pieces[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(createIfMissing = false), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun secretKey(createIfMissing: Boolean = true): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@synchronized it }

        check(createIfMissing) { "Secure storage key is unavailable" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun fromB64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)

    companion object {
        private val keyLock = Any()
        private const val PREFS_NAME = "iumrah.secure.kepler"
        private const val KEY_ALIAS = "com.iumrah.beta.kepler.secure.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
