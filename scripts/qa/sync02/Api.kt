package com.iumrah.beta.core.network
sealed class APIException: Exception() {
    class Status(val code: Int): APIException()
    class Server(val code: Int): APIException()
    object InvalidResponse: APIException()
}
class APIClient {
    var reply: suspend (String, Any?, Long?) -> Any = { _, _, _ -> error("Unexpected request") }
    @Suppress("UNCHECKED_CAST") suspend fun <T,B> post(path: String, body: B, headers: Map<String,String> = emptyMap(), timeoutSeconds: Long? = null): T = reply(path,body,timeoutSeconds) as T
    @Suppress("UNCHECKED_CAST") suspend fun <T> get(path: String, query: Map<String,String?> = emptyMap(), headers: Map<String,String> = emptyMap(), timeoutSeconds: Long? = null): T = reply(path,query,timeoutSeconds) as T
}
