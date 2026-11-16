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
 suspend fun registerPushDevice(id:String,headers:Map<String,String>,token:String,locale:String)=PushResponse(true)
 suspend fun updateHotelSelection(id:String,headers:Map<String,String>,role:String,hotel:HotelSummary,room:HotelRoom?,category:IumrahRoomCategoryOption?) {}
 suspend fun updateContacts(id:String,headers:Map<String,String>,telegram:String,whatsapp:String) {}
 suspend fun updateZiyarat(id:String,headers:Map<String,String>,makkah:Boolean,madinah:Boolean) {}
 suspend fun updateESIM(id:String,headers:Map<String,String>,enabled:Boolean) {}
 suspend fun deleteBooking(id:String,headers:Map<String,String>) {}
 data class PushResponse(val ready:Boolean?)
}
