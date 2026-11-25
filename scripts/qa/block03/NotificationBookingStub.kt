package com.iumrah.beta.models.booking
data class Booking(val createdAt:String)
data class StoredBookingSession(val id:String,val booking:Booking,val accessToken:String="proof",val displayPilgrimID:String?=null,val displayBookingNumber:String="42",val hasServerIdentity:Boolean=true,val effectiveStatus:String="NEW")
