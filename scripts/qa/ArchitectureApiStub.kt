// JVM test transport only; never included in the Android application.
package com.iumrah.beta.core.network
import com.iumrah.beta.data.flight.IumrahFlightDetailResult
import com.iumrah.beta.data.flight.IumrahFlightOperationalDetail
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
sealed class APIException: Exception() {
    class Status(val code:Int): APIException()
    class Server(val code:Int): APIException()
    class Decoding: APIException()
}
class APIClient {
    val calls=AtomicInteger(); @Volatile var fail=false
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(path:String,query:Map<String,String>,timeoutSeconds:Long): T {
        check(path=="/api/package/flights/detail" && query.keys==setOf("number","date","time","origin","destination") && timeoutSeconds==15L)
        calls.incrementAndGet(); delay(50)
        return (if(fail) IumrahFlightDetailResult() else IumrahFlightDetailResult(IumrahFlightOperationalDetail(aircraft="A320"))) as T
    }
}
