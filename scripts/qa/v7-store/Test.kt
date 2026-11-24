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
 check(store.canOpenBookingFromPush("b"))
 check(store.acceptsPushOwner("123","b"))
 check(!store.acceptsPushOwner("456","b"))
 check(!store.canOpenBookingFromPush("unknown"))
 check(store.acceptsPushOwner(null,"b"))
 val unrelated=BookingStore(svc,acc,SecureJsonStore(listOf(StoredBookingSession("b","guest-proof",remote,pilgrimID="456"))),RemotePackageEngineClient())
 unrelated.linkLocalBookingsToAccount(); check(acc.links==0)
 check(unrelated.defaultCareChatKey()=="b")
 check(unrelated.acceptsPushOwner("456","b"))
 check(!unrelated.acceptsPushOwner("789","b"))
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
 // A paid booking cannot be deleted, failed server acknowledgement never purges local proof.
 check(runCatching{confirmation.deleteBooking("b")}.isFailure && readSvc.deleteCalls==0)
 val deleteSvc=BookingService();val unpaid=BookingStore(deleteSvc,guest,SecureJsonStore(listOf(StoredBookingSession("b","proof",remote))),RemotePackageEngineClient())
 deleteSvc.deletion=BookingMutationResponse(ok=false,deleted=false)
 check(runCatching{unpaid.deleteBooking("b")}.isFailure && unpaid.booking("b")!=null)
 deleteSvc.deletion=BookingMutationResponse(ok=true,deleted=true);unpaid.deleteBooking("b");check(unpaid.booking("b")==null)
 deleteSvc.recovered=BookingCreateResponse(remote,"restored-proof",trip.copy(paymentStatus="paid",completedAt="2026-12-05"))
 val restored=unpaid.recoverGuestBooking("42","email","challenge","123456")
 check(restored.accessToken=="restored-proof" && restored.effectiveStatus=="BOOKING_CONFIRMED" && restored.operationPaymentStatus=="paid" && restored.verifiedBookingEmail=="guest@example.com")
 check(unpaid.booking("b")!=null)
 check(runCatching{unpaid.recoverGuestBooking("42","email","challenge","bad")}.isFailure)
 // P14: explicit server acknowledgment and mutation race protection.
 val mutateSvc=BookingService(); val mutateVault=SecureJsonStore(listOf(StoredBookingSession("b","proof",remote)))
 val mutateStore=BookingStore(mutateSvc,guest,mutateVault,RemotePackageEngineClient())
 mutateSvc.mutation=BookingMutationResponse(ok=false)
 check(runCatching{mutateStore.updateContacts("b","tg","wa")}.isFailure)
 check(mutateStore.booking("b")!!.telegram==null && mutateVault.writes==0)
 mutateSvc.mutation=BookingMutationResponse(ok=true)
 mutateStore.updateContacts("b"," tg "," wa "); check(mutateStore.booking("b")!!.telegram=="tg")
 mutateSvc.mutationDelay=25
 awaitAll(async{mutateStore.updateContacts("b","new","wa")},async{mutateStore.updateESIM("b",true)})
 check(mutateStore.booking("b")!!.telegram=="new" && mutateStore.booking("b")!!.esimOverride==true)
 // Old response after a new account logs in must not mutate its account-only trip.
 val scopeAcc=IumrahAccountStore(); val scopeVault=SecureJsonStore(listOf(StoredBookingSession("b","",remote,accountOwnerID="123")))
 val scopeStore=BookingStore(mutateSvc,scopeAcc,scopeVault,RemotePackageEngineClient())
 val mutation=async(start=CoroutineStart.UNDISPATCHED){runCatching{scopeStore.updateContacts("b","leak","leak")}}
 scopeAcc.state.value=IumrahAccountStore.State("456");scopeAcc.bearerToken="other";scopeStore.reconcileAccountScope()
 check(mutation.await().isFailure && scopeStore.booking("b")==null)
 // Keystore failure must not overwrite the only saved access proof.
 val damagedVault=SecureJsonStore(listOf(StoredBookingSession("b","proof",remote))).also{it.readFailure=true}
 val damaged=BookingStore(mutateSvc,guest,damagedVault,RemotePackageEngineClient())
 check(damaged.state.value.storageUnavailable)
 check(runCatching{damaged.adoptOfferBooking(offerReceipt,"pkg","email","guest@example.com")}.isFailure)
 check(damagedVault.writes==0 && damagedVault.rows.single().accessToken=="proof")
 // Wrong booking id / negative acknowledgement cannot publish private eSIM data.
 val esimSvc=BookingService().also { it.operational=ClientTripResponse(false,trip) }
 val esimStore=BookingStore(esimSvc,guest,SecureJsonStore(listOf(StoredBookingSession("b","proof",remote))),RemotePackageEngineClient())
 check(runCatching{esimStore.loadESIMs("b")}.isFailure)
 esimSvc.operational=ClientTripResponse(true,trip.copy(bookingID="other"))
 check(runCatching{esimStore.loadESIMs("b")}.isFailure)
 println("P14 booking mutations, account switch, secure store, eSIM regression: passed")
 println("BookingStore runtime with fake transport/vault: legacy and seamless-guest assertions passed")
}
