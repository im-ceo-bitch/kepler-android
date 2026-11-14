package com.iumrah.beta.core.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ResponseBoundsTest {
    @Test fun exactLimitIsAllowed() {
        val bytes = ByteArray(8192) { it.toByte() }
        assertArrayEquals(bytes, readBounded(ByteArrayInputStream(bytes), bytes.size))
    }

    @Test(expected = ResponseTooLargeException::class)
    fun unknownLengthCannotExceedLimit() {
        readBounded(ByteArrayInputStream(ByteArray(8193)), 8192)
    }

    @Test(expected = ResponseTooLargeException::class)
    fun declaredLengthCannotExceedLimit() {
        readBounded(ByteArrayInputStream(byteArrayOf()), 8192, 8193)
    }
}
