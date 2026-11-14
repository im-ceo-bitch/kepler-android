package com.iumrah.beta.data.chat

import java.net.URLEncoder
import java.security.MessageDigest

object ChatDelta {
    fun path(bookingID: String, after: String, unread: List<String>): String {
        fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "/api/catalog/hotels/client/chats/${encode(bookingID)}/messages?after=${encode(after)}" +
            unread.distinct().take(80).joinToString("") { "&unread=${encode(it)}" }
    }
    fun scope(bookingID: String, headers: Map<String, String>): String? {
        if (headers.isEmpty()) return null
        val raw = headers.toSortedMap().entries.joinToString("\n") { "${it.key}:${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bookingID + ":" + digest.joinToString("") { "%02x".format(it) }
    }
    fun <T> merge(old: List<T>, fresh: List<T>, id: (T) -> String, time: (T) -> String): List<T> =
        (old + fresh).associateBy(id).values.sortedWith(compareBy(time, id))
}
