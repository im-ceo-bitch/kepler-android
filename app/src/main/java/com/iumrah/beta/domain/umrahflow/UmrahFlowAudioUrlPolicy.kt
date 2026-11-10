package com.iumrah.beta.domain.umrahflow

import java.net.URI

/** Only public recordings from the project source may be played. */
object UmrahFlowAudioUrlPolicy {
    fun isAllowed(raw: String): Boolean = try {
        if (raw.length !in 20..2048) false else {
            val uri = URI(raw)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("coaqrsapnpyutsxflsru.supabase.co", ignoreCase = true) &&
                uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.rawPath.startsWith("/storage/v1/object/public/") &&
                !uri.rawPath.contains("..")
        }
    } catch (_: Exception) { false }
}
