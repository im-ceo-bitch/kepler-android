package com.iumrah.beta.core.network
sealed class APIException:Exception(){class Status(val code:Int):APIException(); class Server(val code:Int,val serverMessage:String):APIException(); data object MissingBookingToken:APIException()}
class APIClient {
 var onGet: suspend (String, Map<String,String>) -> Any = {_,_->error("unexpected")}
 suspend inline fun <reified T> get(path:String,query:Map<String,String?> = emptyMap(),headers:Map<String,String> = emptyMap(),timeoutSeconds:Long?=null):T = onGet(path,headers) as T
 suspend inline fun <reified T,reified B> post(path:String,body:B,headers:Map<String,String> = emptyMap(),timeoutSeconds:Long?=null):T = error("unexpected")
 suspend inline fun <reified T> upload(path:String,data:ByteArray,contentType:String,headers:Map<String,String>,timeoutSeconds:Long):T = error("unexpected")
 suspend fun fetchData(path:String,headers:Map<String,String>,timeoutSeconds:Long):ByteArray = error("unexpected")
}
