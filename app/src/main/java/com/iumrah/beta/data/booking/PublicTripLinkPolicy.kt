package com.iumrah.beta.data.booking

import java.net.URI

/** Only the viewer token may leave the authenticated booking boundary. */
object PublicTripLinkPolicy {
    fun isPublicShare(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "iumrah.app" &&
            uri.port in setOf(-1, 443) && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.matches(Regex("/s/[A-Za-z0-9_-]{43}"))
    }.getOrDefault(false)
}
