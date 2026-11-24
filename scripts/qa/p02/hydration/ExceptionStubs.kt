package com.iumrah.beta.core.network
sealed class APIException(message:String):Exception(message) {
    class Status(val code:Int):APIException("HTTP $code")
    class Server(val code:Int):APIException("Server $code")
}
