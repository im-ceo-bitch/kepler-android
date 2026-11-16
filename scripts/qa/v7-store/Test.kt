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
 // V9: same store, real creation lock; fake remote service counts writes.
 val createService=BookingService().also { it.remote=remote; it.operational=ClientTripResponse(true,trip) }
 val guest=IumrahAccountStore().also { it.state.value=IumrahAccountStore.State(null); it.bearerToken=null }
 val checkout=BookingStore(createService,guest,SecureJsonStore(emptyList()),RemotePackageEngineClient())
 val journey=com.iumrah.beta.domain.journey.JourneyState()
 val quote=com.iumrah.beta.domain.pricing.PackageQuote("proof",true,"server-test")
 suspend fun create()=checkout.create(journey,quote,com.iumrah.beta.core.settings.AppLanguage.RUSSIAN,null)
 val pending=async(start=CoroutineStart.UNDISPATCHED){create()}
 check(runCatching{create()}.exceptionOrNull() is IllegalStateException)
 check(pending.await().id=="b"); check(createService.createCalls==1); check(!checkout.state.value.isMutating)
 createService.createError=APIException.Status(503)
 check(runCatching{create()}.exceptionOrNull() is APIException.Status)
 check(!checkout.state.value.isMutating)
 createService.createError=null
 val cancelled=launch(start=CoroutineStart.UNDISPATCHED){create()}; cancelled.cancelAndJoin()
 check(!checkout.state.value.isMutating); check(create().id=="b"); check(createService.createCalls==4)
 // Sync05: read-only confirmation poll, reject wrong booking, preserve snapshot on errors.
 val readSvc=BookingService().also { it.remote=remote; it.operational=ClientTripResponse(true,trip) }
 val confirmation=BookingStore(readSvc,guest,SecureJsonStore(listOf(StoredBookingSession("b","proof",remote))),RemotePackageEngineClient())
 check(confirmation.refreshCreatedBookingStatus("b")?.effectiveStatus=="BOOKING_CONFIRMED")
 check(readSvc.fetchCalls==0 && readSvc.operationalCalls==1)
 check(readSvc.operationalHeaders==mapOf("x-booking-token" to "proof"))
 readSvc.operational=ClientTripResponse(true,trip.copy(bookingID="other",status="cancelled"))
 check(confirmation.refreshCreatedBookingStatus("b")?.effectiveStatus=="BOOKING_CONFIRMED")
 readSvc.operationalError=APIException.Status(503)
 check(confirmation.refreshCreatedBookingStatus("b")?.effectiveStatus=="BOOKING_CONFIRMED")
 check(confirmation.refreshCreatedBookingStatus("missing")==null)
 val noToken=BookingStore(readSvc,guest,SecureJsonStore(listOf(StoredBookingSession("b","",remote))),RemotePackageEngineClient())
 val calls=readSvc.operationalCalls; noToken.refreshCreatedBookingStatus("b"); check(readSvc.operationalCalls==calls)
 println("BookingStore runtime with fake transport/vault: 27 assertions passed (including Sync05 confirmation polling)")
}
