import com.iumrah.beta.data.chat.*
import com.iumrah.beta.core.network.*
import com.iumrah.beta.models.booking.*
import kotlinx.coroutines.*
fun main() = runBlocking {
 val api=APIClient(); val svc=ChatService(api); val headers=mapOf("x-booking-token" to "test")
 fun msg(id:String, read:Boolean=false)=ChatMessage(id,"b","client",body="test",createdAt="2026-11-16T00:00:00Z",readByStaff=read)
 var calls=0
 api.onGet={path,_-> when(++calls){
 1->{check(path.endsWith("after=0")); ChatListResponse(messages=listOf(msg("a")),nextCursor="opaque:1",scopeVersion="s1",hasMore=true)}
 2->{check(path.contains("after=opaque%3A1&scope=s1&unread=a")); ChatListResponse(messages=listOf(msg("b")),nextCursor="opaque:2",scopeVersion="s1",staffReadMessageIDs=listOf("a"))}
 3->{check(path.contains("after=opaque%3A2&scope=s1&unread=b")); ChatListResponse(messages=listOf(msg("a")),nextCursor="opaque:3",scopeVersion="s1")}
 4->{ChatListResponse(messages=listOf(msg("c")),nextCursor="opaque:4",scopeVersion="s2",reset=true)}
 else->error("extra call")
 }}
 check(svc.loadChat("b",headers).map{it.id}==listOf("a","b"));check(svc.cachedChat("b",headers).first().readByStaff==true)
 check(svc.loadChat("b",headers).first().readByStaff==true)
 check(svc.loadChat("b",headers).map{it.id}==listOf("c"))
 check(svc.cachedChat("b",mapOf("x-booking-token" to "other")).isEmpty())
 svc.clearCache();check(svc.cachedChat("b",headers).isEmpty())
 val accountHeaders=mapOf("Authorization" to "Bearer account-A")
 api.onGet={path, proof ->
   check(path.contains("/chats/account/messages"));check(proof==accountHeaders)
   ChatListResponse(messages=listOf(msg("account-msg"),msg("other-trip").copy(bookingID="trip-two")),nextCursor="account:1")
 }
 check(svc.loadChat("account",accountHeaders).map{it.id}==listOf("account-msg","other-trip"))
 check(svc.cachedChat("account",mapOf("Authorization" to "Bearer account-B")).isEmpty())
 check(svc.mergeChat("account",accountHeaders,listOf(msg("sent").copy(bookingID="trip-two"))).size==3)
 check(svc.mergeChat("b",headers,listOf(msg("foreign").copy(bookingID="trip-two"))).isEmpty())
 println("ChatService: paging, cursor, read receipts, reset, auth scope, clear and account thread passed")
}
