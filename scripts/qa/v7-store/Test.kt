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
 val vault=SecureJsonStore(listOf(StoredBookingSession("b","proof",remote,pilgrimID="00000123")))
 val store=BookingStore(svc,acc,vault,RemotePackageEngineClient())
 check(store.defaultCareChatKey()=="account")
 check(store.careChatKeyForPush("account","00000123")=="account")
 check(store.careChatKeyForPush("b","456")==null)
 val unrelated=BookingStore(svc,acc,SecureJsonStore(listOf(StoredBookingSession("b","guest-proof",remote,pilgrimID="456"))),RemotePackageEngineClient())
 unrelated.linkLocalBookingsToAccount(); check(acc.links==0)
 check(unrelated.defaultCareChatKey()=="b")
 check(unrelated.careHeaders("b")==mapOf("x-booking-token" to "guest-proof"))
 check(unrelated.careHeaders("account").isEmpty())
 check(unrelated.headersFor(unrelated.booking("b")!!)==mapOf("x-booking-token" to "guest-proof"))
 check(unrelated.careChatKeyForPush("account","456")=="b")
 unrelated.syncPushSubscriptions("AbC:CaseSensitive_FCM", "ru")
 check(svc.pushToken=="AbC:CaseSensitive_FCM" && svc.pushHeaders==mapOf("x-booking-token" to "guest-proof"))
 check(unrelated.state.value.pushRegistrationReady==true)
 svc.pushOK=false; unrelated.syncPushSubscriptions("AbC:CaseSensitive_FCM", "ru")
 check(unrelated.state.value.pushRegistrationReady==false && unrelated.state.value.pushRegistrationError!=null)
 svc.pushOK=true
 val closed=BookingStore(svc,acc,SecureJsonStore(listOf(StoredBookingSession("b","proof",remote,operationStatus="deleted",pilgrimID="123"))),RemotePackageEngineClient())
 check(closed.defaultCareChatKey()==null && closed.careHeaders("b").isEmpty())
 store.linkLocalBookingsToAccount(); store.linkLocalBookingsToAccount(); check(acc.links==1); check(store.booking("b")!!.displayBookingNumber=="#0042")
 svc.fetchError=APIException.Status(401); check(store.refresh("b")!!.operationStatus=="paid"); check(acc.detailCalls==1)
 svc.fetchError=APIException.Status(503); check(runCatching{store.refresh("b")}.isFailure); check(acc.detailCalls==1); check(store.booking("b")!=null)
 svc.fetchError=null; val before=svc.fetchCalls; awaitAll(async{store.refreshAll()},async{store.refreshAll()}); check(svc.fetchCalls==before+1)
 val owned=BookingStore(svc,acc,SecureJsonStore(listOf(StoredBookingSession("b","",remote,accountOwnerID="123"))),RemotePackageEngineClient()); check(owned.booking("b")!=null)
 acc.state.value=IumrahAccountStore.State("456"); acc.bearerToken="tokenB"; owned.reconcileAccountScope(); check(owned.booking("b")==null); check(owned.state.value.sessions.isEmpty())
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
 check(confirmation.defaultCareChatKey()=="b")
 check(readSvc.fetchCalls==0 && readSvc.operationalCalls==1)
 check(readSvc.operationalHeaders==mapOf("x-booking-token" to "proof"))
 readSvc.operational=ClientTripResponse(true,trip.copy(bookingID="other",status="cancelled"))
 check(confirmation.refreshCreatedBookingStatus("b")?.effectiveStatus=="BOOKING_CONFIRMED")
 readSvc.operationalError=APIException.Status(503)
 check(confirmation.refreshCreatedBookingStatus("b")?.effectiveStatus=="BOOKING_CONFIRMED")
 check(confirmation.refreshCreatedBookingStatus("missing")==null)
 val noToken=BookingStore(readSvc,guest,SecureJsonStore(listOf(StoredBookingSession("b","",remote))),RemotePackageEngineClient())
 val calls=readSvc.operationalCalls; noToken.refreshCreatedBookingStatus("b"); check(readSvc.operationalCalls==calls)
 val offerToken="ab".repeat(32); check(BusinessOfferPolicy.validToken(offerToken)); check(!BusinessOfferPolicy.validToken("../"+offerToken)); check(!BusinessOfferPolicy.validToken("a".repeat(63)))
 val offerReceipt=BookingCreateResponse(remote.copy(id="offer-booking",offerPackageID="pkg"),"offer-proof")
 check(!BusinessOfferPolicy.validReceipt("other",offerReceipt)); check(!BusinessOfferPolicy.validReceipt("pkg",offerReceipt.copy(accessToken=null)))
 val oldWrites=createService.createCalls; val accepted=checkout.adoptOfferBooking(offerReceipt,"pkg","email","guest@example.com")
 check(accepted.verifiedBookingEmail=="guest@example.com" && accepted.accessToken=="offer-proof" && accepted.accountOwnerID==null); check(checkout.booking("offer-booking")!=null); check(createService.createCalls==oldWrites)
 checkout.adoptOfferBooking(offerReceipt,"pkg","email","guest@example.com"); check(checkout.state.value.sessions.count{it.id=="offer-booking"}==1)
 println("BookingStore runtime with fake transport/vault: legacy and seamless-guest assertions passed")
}
