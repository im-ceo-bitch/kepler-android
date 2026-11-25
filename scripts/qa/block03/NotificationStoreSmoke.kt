import android.content.Context
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.notification.*
import kotlinx.coroutines.*
import java.time.Instant
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.core.settings.AppLanguage

fun main() = runBlocking {
    var account:String?="A"; var trips=listOf("trip-1")
    val api=APIClient(); val store=ClientNotificationStore(Context(),api,{account},{trips})
    fun item(id:String,read:Boolean=false)=ClientSystemNotification(id,id,"test","account","booking","trip-1","iumrah","published",createdAt="2026-11-25T01:00:00Z",expiresAt="2027-01-01T00:00:00Z",isRead=read)
    val a=item("A-only"); val b=item("B-only")
    val started=CompletableDeferred<Unit>(); val unblock=CompletableDeferred<Unit>()
    api.get={_,headers-> if(headers["Authorization"]=="Bearer A") { started.complete(Unit); unblock.await(); ClientNotificationFeedResponse(true,listOf(a)) } else ClientNotificationFeedResponse(true,listOf(b)) }
    val old=launch { store.refresh("A") }; started.await()
    account="B"; store.reconcileScope(); check(store.state.value.inbox.isEmpty())
    store.refresh("B"); check(store.state.value.inbox.single().id=="B-only")
    unblock.complete(Unit); old.join(); check(store.state.value.inbox.single().id=="B-only")
    var reads=0
    api.post={path,_,headers->check(headers["Authorization"]=="Bearer B");check(path.endsWith("/read"));reads++;ClientNotificationReadResponse(true)}
    store.markOpened(a,"A");check(reads==0)
    store.markOpened(b,"B");check(reads==1 && store.state.value.inbox.single().isRead)
    store.refresh("B");check(store.state.value.inbox.single().isRead)
    api.get={_,_->ClientNotificationFeedResponse(false,emptyList())}
    store.refresh("B");check(store.state.value.inbox.single().id=="B-only" && store.state.value.lastError!=null)
    val before=reads
    store.syncBookingReceipts(listOf(StoredBookingSession("trip-1",Booking(Instant.now().toString()))),AppLanguage.ENGLISH)
    val receipt=store.state.value.bookingReceipts.single();store.markOpened(receipt,"B");check(reads==before && store.state.value.bookingReceipts.single().isRead)
    trips=emptyList();store.reconcileScope();check(store.state.value.inbox.isEmpty())
    var registrations=0
    api.post={path,body,headers->check(path.endsWith("/devices"));check(headers["Authorization"]=="Bearer B");check((body as ClientNotificationDeviceRegistration).deviceToken=="FCM_CaseSensitive");registrations++;ClientNotificationDeviceResponse(true,true)}
    api.get={_,_->ClientNotificationFeedResponse(true,listOf(b))}
    store.sync("FCM_CaseSensitive","B",false,"ru");check(registrations==1 && store.state.value.pushProviderReady==true && !store.state.value.isSyncing)
    val registrationStart=CompletableDeferred<Unit>();val registrationRelease=CompletableDeferred<Unit>()
    val authOrder=mutableListOf<String?>()
    api.post={_,_,headers->authOrder+=headers["Authorization"]; if(headers["Authorization"]=="Bearer B") {registrationStart.complete(Unit);registrationRelease.await()};ClientNotificationDeviceResponse(true,true)}
    val previous=launch {store.sync("FCM_CaseSensitive","B",false,"ru")};registrationStart.await()
    account=null;store.reconcileScope();val guest=launch {store.sync("FCM_CaseSensitive",null,false,"ru")};yield();check(authOrder==listOf("Bearer B"))
    api.get={_,headers->check(headers.isEmpty());ClientNotificationFeedResponse(true,emptyList())}
    registrationRelease.complete(Unit);previous.join();guest.join();check(authOrder==listOf("Bearer B",null));check(!store.state.value.isSyncing && store.state.value.inbox.isEmpty())
    println("Block03 notification store: stale feed, scoped reads, ok=false, local receipt, token case, serial registration and logout PASS (mock transport/preferences)")
}
