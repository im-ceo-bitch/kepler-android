import com.iumrah.beta.domain.booking.ItineraryResolution
import com.iumrah.beta.models.booking.BookingItineraryResponse
import com.iumrah.beta.models.booking.BookingItineraryItem
import com.iumrah.beta.core.network.APIException
import kotlinx.coroutines.*

fun main() = runBlocking {
    fun item(id: String, day: String, order: Int, booking: String = "b") = BookingItineraryItem(id, booking, day, order, id, "", "", "", "", "", "")
    val items = listOf(item("c","2026-12-02",1), item("b","2026-12-01",2), item("a","2026-12-01",1))
    fun reply(managed: Boolean?, rows: List<BookingItineraryItem> = items, id: String = "b", ok: Boolean = true) = BookingItineraryResponse(ok,id,rows,managed=managed)
    val calls = mutableListOf<String>()
    suspend fun load(first: BookingItineraryResponse?, generated: BookingItineraryResponse?, final: BookingItineraryResponse = reply(false)): BookingItineraryResponse {
        calls.clear()
        return ItineraryResolution.resolve("b", { localized -> calls += if(localized) "localized" else "operational"; if(localized) final else first ?: throw APIException.Status(404) }, { calls += "package"; generated ?: throw APIException.Status(404) })
    }
    check(load(reply(true), reply(false)).items.map{it.id} == listOf("a","b","c")); check(calls == listOf("operational"))
    check(load(reply(true, emptyList()), reply(false)).items.isEmpty()); check(calls == listOf("operational"))
    check(load(reply(false), reply(null)).items.size==3); check(calls == listOf("operational","package"))
    check(load(null,reply(false)).items.size==3); check(calls == listOf("operational","package"))
    check(load(reply(false),reply(false, emptyList())).items.size==3); check(calls == listOf("operational","package","localized"))
    check(load(null,null).items.size==3); check(calls == listOf("operational","package","localized"))
    check(runCatching { load(reply(true,id="other"), reply(false)) }.isFailure); check(calls==listOf("operational"))
    check(runCatching { load(reply(true,listOf(item("x","2026-12-01",1,"other"))),reply(false)) }.isFailure)
    check(runCatching { load(reply(true,ok=false),reply(false)) }.isFailure)
    var fallback=false
    check(runCatching{ItineraryResolution.resolve("b", {throw APIException.Status(403)}, {fallback=true;reply(false)})}.exceptionOrNull() is APIException.Status); check(!fallback)
    check(runCatching{ItineraryResolution.resolve("b", {throw CancellationException("cancel")}, {fallback=true;reply(false)})}.exceptionOrNull() is CancellationException); check(!fallback)
    println("P14 itinerary resolution: 19 assertions passed")
}
