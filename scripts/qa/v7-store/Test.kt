import com.iumrah.beta.data.booking.*
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.account.IumrahAccountTripDetailResponse
import kotlinx.coroutines.*
fun main()=runBlocking {
 val remote=RemoteBooking("b","new","economy",100.0,100.0,BookingInputRecord("2026-12-01","2026-12-05","TAS","TAS","JED",false,BookingTravelers(1,0,0,1)),BookingRoute("TAS","JED","JED"),BookingStay(5,4,"2026-12-01","2026-12-05",4),BookingHotelNames("h",""),"flight","2026-11-01","2026-11-01")
 val trip=ClientTripSnapshot("t","b",42,"#0042","123","paid")
 val svc=BookingService(); svc.remote=remote; svc.operational=ClientTripResponse(true,trip)
 val acc=IumrahAccountStore(); acc.detail=IumrahAccountTripDetailResponse(true,trip,remote)
 val vault=SecureJsonStore(listOf(StoredBookingSession("b","proof",remote)))
 val store=BookingStore(svc,acc,vault,RemotePackageEngineClient())
 store.linkLocalBookingsToAccount(); store.linkLocalBookingsToAccount(); check(acc.links==1); check(store.booking("b")!!.displayBookingNumber=="#0042")
 svc.fetchError=APIException.Status(401); check(store.refresh("b")!!.operationStatus=="paid"); check(acc.detailCalls==1)
 svc.fetchError=APIException.Status(503); check(runCatching{store.refresh("b")}.isFailure); check(acc.detailCalls==1); check(store.booking("b")!=null)
 svc.fetchError=null; val before=svc.fetchCalls; awaitAll(async{store.refreshAll()},async{store.refreshAll()}); check(svc.fetchCalls==before+1)
 val owned=BookingStore(svc,acc,SecureJsonStore(listOf(StoredBookingSession("b","",remote,accountOwnerID="A"))),RemotePackageEngineClient()); check(owned.booking("b")!=null)
 acc.state.value=IumrahAccountStore.State("B"); acc.bearerToken="tokenB"; owned.reconcileAccountScope(); check(owned.booking("b")==null); check(owned.state.value.sessions.isEmpty())
 println("BookingStore runtime with fake transport/vault: 11 assertions passed")
}
