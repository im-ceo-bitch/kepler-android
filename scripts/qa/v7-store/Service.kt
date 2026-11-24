package com.iumrah.beta.data.booking
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
class BookingService {
 var fetchError:Throwable?=null; var remote:RemoteBooking?=null; var operational:ClientTripResponse?=null; var fetchCalls=0; var operationalCalls=0; var operationalError:Throwable?=null; var operationalHeaders:Map<String,String> = emptyMap(); var createCalls=0; var createError:Throwable?=null
 suspend fun fetchBooking(id:String,token:String):RemoteBooking { fetchCalls++; kotlinx.coroutines.delay(40); fetchError?.let{throw it}; return requireNotNull(remote) }
 suspend fun fetchOperationalTrip(id:String,headers:Map<String,String>):ClientTripResponse { operationalCalls++; operationalHeaders=headers; kotlinx.coroutines.delay(10); operationalError?.let{throw it}; return requireNotNull(operational) }
 suspend fun createBooking(request:BookingCreateEnvelope):BookingCreateResponse {
     createCalls++; kotlinx.coroutines.delay(60); createError?.let { throw it }
     return BookingCreateResponse(requireNotNull(remote),"proof")
 }
 suspend fun syncBookingProfile(id:String,token:String,profile:BookingPilgrimProfile,trace:BookingGeneratorTrace?=null):ClientTripResponse = requireNotNull(operational)
 suspend fun syncGeneratorReport(id:String,token:String,trace:BookingGeneratorTrace?):ClientTripResponse = requireNotNull(operational)
 var pushCalls=0; var pushHeaders:Map<String,String> = emptyMap(); var pushToken:String?=null; var pushOK=true
 suspend fun registerPushDevice(id:String,headers:Map<String,String>,token:String,locale:String):PushResponse { pushCalls++; pushHeaders=headers; pushToken=token; return PushResponse(true,pushOK,id) }
 var mutation = BookingMutationResponse(ok=true); var mutationDelay = 0L
 suspend fun updateHotelSelection(id:String,headers:Map<String,String>,role:String,hotel:HotelSummary,room:HotelRoom?,category:IumrahRoomCategoryOption?):BookingMutationResponse { kotlinx.coroutines.delay(mutationDelay);return mutation }
 suspend fun updateContacts(id:String,headers:Map<String,String>,telegram:String,whatsapp:String):BookingMutationResponse { kotlinx.coroutines.delay(mutationDelay);return mutation }
 suspend fun updateZiyarat(id:String,headers:Map<String,String>,makkah:Boolean,madinah:Boolean):BookingMutationResponse { kotlinx.coroutines.delay(mutationDelay);return mutation }
 suspend fun updateESIM(id:String,headers:Map<String,String>,enabled:Boolean):BookingMutationResponse { kotlinx.coroutines.delay(mutationDelay);return mutation }
 var deleteCalls=0; var deletion=BookingMutationResponse(ok=true,deleted=true);var recovered:BookingCreateResponse?=null
 suspend fun deleteBooking(id:String,headers:Map<String,String>):BookingMutationResponse {deleteCalls++;return deletion}
 suspend fun confirmGuestContact(channel:String,challengeID:String,code:String):GuestProof { check(code=="123456");return GuestProof("verified","guest@example.com",null) }
 suspend fun recoverBooking(number:String,channel:String,proof:String):BookingCreateResponse {check(proof=="verified");return requireNotNull(recovered)}
 data class GuestProof(val verificationToken:String,val email:String?,val phone:String?)
 data class PushResponse(val ready:Boolean?, val ok:Boolean = true, val bookingID:String? = null)
}
