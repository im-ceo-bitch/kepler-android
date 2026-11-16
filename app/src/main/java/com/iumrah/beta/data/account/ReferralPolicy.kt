package com.iumrah.beta.data.account

import java.net.URI
import java.util.Locale

object ReferralPolicy {
    private val pattern = Regex("^(IUMR-[A-F0-9]{24}|[A-F0-9]{16})$")
    private val hosts = setOf("iumrah.app", "iumrah.uz", "go.iumrah.app", "go.iumrah.uz", "iumrah-backend.helloiumrah.workers.dev")
    fun normalize(raw: String): String? = raw.trim().uppercase(Locale.ROOT).takeIf { pattern.matches(it) }
    fun capture(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.userInfo != null || uri.port != -1) return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val web = uri.scheme == "https" && uri.host in hosts && parts.size == 2 && parts.first() in setOf("ref", "r")
        val app = uri.scheme == "iumrahapp" && uri.host == "referral" && parts.size == 1
        return if (web || app) normalize(parts.last()) else null
    }
    fun headers(code: String?, consent: Boolean): Map<String, String> =
        if (consent) code?.let(::normalize)?.let { mapOf("x-iumrah-referral-code" to it) }.orEmpty() else emptyMap()
}
