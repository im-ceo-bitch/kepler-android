package com.iumrah.beta.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal class ResponseTooLargeException : IOException("Server response exceeds the allowed size.")

/** Limit decoded bytes, not just Content-Length (which may be missing or compressed). */
internal fun readBounded(input: InputStream, limit: Int, contentLength: Long = -1): ByteArray {
    require(limit > 0)
    if (contentLength > limit) throw ResponseTooLargeException()
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - total + 1).toInt())
        if (count == -1) break
        if (count == 0) continue
        if (count > limit - total) throw ResponseTooLargeException()
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
