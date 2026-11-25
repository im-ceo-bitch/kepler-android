package kotlinx.serialization.json
// Deliberately fake disk serializer; scope tests use real ClientNotificationStore, not real disk codec.
class Json(init:Builder.()->Unit) {
    class Builder { var ignoreUnknownKeys=false; var explicitNulls=true; var encodeDefaults=false }
    init { Builder().init() }
    fun <T> encodeToString(serializer:Any,value:T):String = "offline-test-cache"
    fun <T> decodeFromString(serializer:Any,value:String):T = error("Codec not used in this test")
}
