package com.iumrah.beta.domain.journey
import com.iumrah.beta.models.hotel.*
import com.iumrah.beta.domain.trip.TransferVehicleKind
class JourneyState(val priceChangeAccepted:Boolean=true,val trip:com.iumrah.beta.domain.trip.TripDraft=com.iumrah.beta.domain.trip.TripDraft(),val makkahHotel:HotelSummary?=null,val makkahRoom:HotelRoom?=null,val makkahRoomCategory:IumrahRoomCategoryOption?=null,val madinahHotel:HotelSummary?=null,val madinahRoom:HotelRoom?=null,val madinahRoomCategory:IumrahRoomCategoryOption?=null,val resolvedTransferVehicle:TransferVehicleKind?=null)
