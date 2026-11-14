package com.iumrah.beta.core.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Response ownership remains with the callback, even if the caller cancels. */
internal suspend fun <T> Call.awaitResult(consume: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (!continuation.isActive) return
                        Result.success(consume(it))
                    }
                } catch (error: Exception) {
                    Result.failure(error)
                }
                continuation.resumeWith(result)
            }
        })
    }
