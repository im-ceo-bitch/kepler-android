package com.iumrah.beta.data.account
import com.iumrah.beta.models.account.*
import com.iumrah.beta.models.booking.*
import kotlinx.coroutines.flow.MutableStateFlow
class IumrahAccountStore {
 data class State(val iumrahID:String?)
 val state=MutableStateFlow(State("A")); var bearerToken:String?="tokenA"; var detail:IumrahAccountTripDetailResponse?=null; var links=0; var detailCalls=0
 fun authorizationHeaders(bookingToken:String?):Map<String,String> = buildMap { bearerToken?.let{put("Authorization","Bearer $it")}; bookingToken?.takeIf{it.isNotBlank()}?.let{put("x-booking-token",it)} }
 suspend fun accountTrips(token:String):List<ClientTripSnapshot> = listOfNotNull(detail?.trip)
 suspend fun tripDetail(id:String,token:String):IumrahAccountTripDetailResponse { detailCalls++; return requireNotNull(detail) }
 suspend fun linkBookingForAccount(id:String,proof:String,token:String):IumrahAccountLinkBookingResponse { links++; return IumrahAccountLinkBookingResponse(true,"123",42,"#0042") }
}
