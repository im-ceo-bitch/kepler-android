import com.iumrah.beta.core.network.*
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.*
import okhttp3.*
private class FakeCall : Call {
    var callback: Callback? = null
    var cancellations = 0
    override fun cancel() { cancellations++ }
    override fun enqueue(callback: Callback) { this.callback = callback }
}
fun main() = runBlocking {
    var checks = 0
    fun verify(result: Boolean) { check(result); checks++ }
    for (size in listOf(0, 1, 8191, 8192, 8193, 16384)) {
        val bytes = ByteArray(size) { (it % 251).toByte() }
        verify(readBounded(ByteArrayInputStream(bytes), 16384).contentEquals(bytes))
    }
    verify(runCatching { readBounded(ByteArrayInputStream(ByteArray(9)), 8) }.exceptionOrNull() is ResponseTooLargeException)
    verify(runCatching { readBounded(ByteArrayInputStream(byteArrayOf()), 8, 9) }.exceptionOrNull() is ResponseTooLargeException)
    verify(runCatching { readBounded(ByteArrayInputStream(ByteArray(8193)), 8192, 1) }.exceptionOrNull() is ResponseTooLargeException)
    verify(runCatching { readBounded(ByteArrayInputStream(byteArrayOf()), 0) }.isFailure)
    val success = FakeCall(); var closed = 0
    val result = async(start = CoroutineStart.UNDISPATCHED) { success.awaitResult { it.value } }
    success.callback!!.onResponse(success, Response("ok") { closed++ })
    verify(result.await() == "ok"); verify(closed == 1); verify(success.cancellations == 0)
    val cancelled = FakeCall(); var consumed = false
    val job = launch(start = CoroutineStart.UNDISPATCHED) { cancelled.awaitResult { consumed = true } }
    job.cancelAndJoin(); verify(cancelled.cancellations == 1)
    cancelled.callback!!.onResponse(cancelled, Response("late") { closed++ })
    verify(!consumed); verify(closed == 2)
    val decodeFailure = FakeCall()
    val bad = async(start = CoroutineStart.UNDISPATCHED) { runCatching { decodeFailure.awaitResult<Unit> { throw IOException("decode") } } }
    decodeFailure.callback!!.onResponse(decodeFailure, Response("bad") { closed++ })
    verify(bad.await().exceptionOrNull()?.message == "decode"); verify(closed == 3)
    val networkFailure = FakeCall()
    val failed = async(start = CoroutineStart.UNDISPATCHED) { runCatching { networkFailure.awaitResult { it.value } } }
    networkFailure.callback!!.onFailure(networkFailure, IOException("network"))
    verify(failed.await().exceptionOrNull()?.message == "network")
    println("V9 real response bounds and coroutine bridge: $checks assertions passed (fake HTTP transport)")
}
