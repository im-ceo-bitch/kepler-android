package com.iumrah.beta.models.nuitee

data class NuiteeMinimumRate(val hotelId:String="",val price:Double,val currency:String="USD",val offerId:String?=null)
data class NuiteeHotel(val id:String,val name:String="",val roundedStars:Int=3,
    val minimumRate:NuiteeMinimumRate?=null,val rateStatus:String?=null)
data class NuiteeHotelListEnvelope(val hotels:List<NuiteeHotel>,val sandbox:Boolean=false,val hotelCatalogStale:Boolean=false)
