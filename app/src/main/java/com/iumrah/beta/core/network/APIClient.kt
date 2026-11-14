package com.iumrah.beta.core.network

import com.iumrah.beta.core.config.AppConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

sealed class APIException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidResponse : APIException("Сервер вернул некорректный ответ.")
    class Status(val code: Int) : APIException("Сервер временно недоступен ($code).")
    class Server(val code: Int, val serverMessage: String) : APIException(serverMessage)
    class Decoding(cause: Throwable) : APIException("Не удалось прочитать данные сервера.", cause)
    data object MissingBookingToken : APIException("Booking access token is missing.")
}

@Serializable
private data class APIErrorEnvelope(val error: String? = null, val message: String? = null)

class APIClient(
    client: OkHttpClient = defaultClient(),
) {
    // A redirect must not send a booking token or account credential to another origin.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    // Reads may recover from a broken connection; mutation calls must not be
    // transparently replayed by the transport after an ambiguous failure.
    private val mutationClient = this.client.newBuilder().retryOnConnectionFailure(false).build()

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
        coerceInputValues = false
    }

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = requestJson("POST", path, body, serializer<B>(), serializer<T>(), headers, timeoutSeconds)

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = requestNoBody("GET", path, query, serializer<T>(), headers, timeoutSeconds)

    suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = requestJson("PUT", path, body, serializer<B>(), serializer<T>(), headers, timeoutSeconds)

    suspend inline fun <reified T, reified B> patch(
        path: String,
        body: B,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = requestJson("PATCH", path, body, serializer<B>(), serializer<T>(), headers, timeoutSeconds)

    suspend inline fun <reified T> delete(
        path: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = requestNoBody("DELETE", path, emptyMap(), serializer<T>(), headers, timeoutSeconds)

    suspend inline fun <reified T> upload(
        path: String,
        data: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): T = upload(path, data, contentType, serializer<T>(), headers, timeoutSeconds)

    suspend fun fetchData(
        path: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): ByteArray {
        val url = absoluteUrl(path)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return execute(request, timeoutSeconds) { response ->
            checkedBody(response, MAX_MEDIA_BYTES)
        }
    }

    @PublishedApi
    internal suspend fun <T, B> requestJson(
        method: String,
        path: String,
        body: B,
        bodySerializer: KSerializer<B>,
        responseSerializer: KSerializer<T>,
        headers: Map<String, String>,
        timeoutSeconds: Long?,
    ): T {
        val payload = try {
            json.encodeToString(bodySerializer, body)
        } catch (error: SerializationException) {
            throw APIException.Decoding(error)
        }
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = baseRequest(path, headers).method(method, requestBody).build()
        return executeAndDecode(request, responseSerializer, timeoutSeconds)
    }

    @PublishedApi
    internal suspend fun <T> requestNoBody(
        method: String,
        path: String,
        query: Map<String, String?>,
        responseSerializer: KSerializer<T>,
        headers: Map<String, String>,
        timeoutSeconds: Long?,
    ): T {
        val urlBuilder = absoluteUrl(path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> if (value != null) urlBuilder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .method(method, null)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return executeAndDecode(request, responseSerializer, timeoutSeconds)
    }

    @PublishedApi
    internal suspend fun <T> upload(
        path: String,
        data: ByteArray,
        contentType: String,
        responseSerializer: KSerializer<T>,
        headers: Map<String, String>,
        timeoutSeconds: Long?,
    ): T {
        val request = baseRequest(path, headers)
            .post(data.toRequestBody(contentType.toMediaType()))
            .build()
        return executeAndDecode(request, responseSerializer, timeoutSeconds)
    }

    private fun baseRequest(path: String, headers: Map<String, String>): Request.Builder = Request.Builder()
        .url(absoluteUrl(path))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("User-Agent", USER_AGENT)
        .apply { headers.forEach { (k, v) -> header(k, v) } }

    private suspend fun <T> executeAndDecode(
        request: Request,
        serializer: KSerializer<T>,
        timeoutSeconds: Long?,
    ): T = execute(request, timeoutSeconds) { response ->
        val bytes = checkedBody(response, MAX_JSON_BYTES)
        val text = bytes.toString(Charsets.UTF_8)
        try {
            json.decodeFromString(serializer, text)
        } catch (error: SerializationException) {
            throw APIException.Decoding(error)
        }
    }

    private suspend fun <T> execute(request: Request, timeoutSeconds: Long?, consume: (Response) -> T): T {
        require(timeoutSeconds == null || timeoutSeconds > 0)
        val base = if (request.method == "GET" || request.method == "HEAD") client else mutationClient
        val selectedClient = if (timeoutSeconds != null && timeoutSeconds > 25) base.newBuilder()
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS).build() else base
        val call = selectedClient.newCall(request)
        if (timeoutSeconds != null) call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        return call.awaitResult(consume)
    }

    private fun checkedBody(response: Response, limit: Int): ByteArray {
        // Preserve status even if an upstream error page is huge or malformed.
        if (!response.isSuccessful) {
            val bytes = try { readBounded(response.body.byteStream(), MAX_ERROR_BYTES) }
            catch (_: java.io.IOException) { ByteArray(0) }
            throw errorFor(response, bytes)
        }
        return readBounded(response.body.byteStream(), limit, response.body.contentLength())
    }

    private fun errorFor(response: Response, bytes: ByteArray): APIException {
        val envelope = runCatching {
            json.decodeFromString(APIErrorEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
        }.getOrNull()
        val message = envelope?.error?.takeIf { it.isNotBlank() }
            ?: envelope?.message?.takeIf { it.isNotBlank() }
        return if (message != null) APIException.Server(response.code, message) else APIException.Status(response.code)
    }

    private fun absoluteUrl(path: String): String = AppConfig.apiUrl(path)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_JSON_BYTES = 16 * 1024 * 1024
        private const val MAX_MEDIA_BYTES = 64 * 1024 * 1024
        private const val MAX_ERROR_BYTES = 64 * 1024
        private const val USER_AGENT = "iumrah-android-beta/0.2"

        private val sharedTransport by lazy { OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build() }

        private fun defaultClient(): OkHttpClient = sharedTransport
    }
}
