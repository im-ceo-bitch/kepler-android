package com.iumrah.beta.core.network
class APIClient { suspend inline fun <reified T> get(path:String,query:Map<String,String?>,timeoutSeconds:Int):T = error("offline test") }
