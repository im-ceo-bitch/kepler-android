package com.iumrah.beta.core.network
sealed class APIException:Exception("Invalid response") { data object InvalidResponse:APIException() }
class APIClient {
    var get: suspend (String,Map<String,String>) -> Any = {_,_->error("Unexpected GET")}
    var post: suspend (String,Any?,Map<String,String>) -> Any = {_,_,_->error("Unexpected POST")}
    suspend inline fun <reified T> get(path:String,query:Map<String,String?> = emptyMap(),headers:Map<String,String> = emptyMap()):T = get.invoke(path,headers) as T
    suspend inline fun <reified T,reified B> post(path:String,body:B,headers:Map<String,String> = emptyMap()):T = post.invoke(path,body,headers) as T
}
