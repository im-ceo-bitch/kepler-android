package com.iumrah.beta.data.telegram

import com.iumrah.beta.core.network.APIClient
import kotlinx.serialization.Serializable

@Serializable data class TelegramLinkRequest(val bookingId:String,val language:String)
@Serializable data class TelegramBookingRef(val bookingID:String?=null,val bookingDisplayNumber:String?=null)
@Serializable data class TelegramLinkResponse(val ok:Boolean=false,val booking:TelegramBookingRef?=null,val startParameter:String?=null,val linkUrl:String?=null,val expiresAt:String?=null)
@Serializable data class TelegramStatusResponse(val ok:Boolean=false,val linked:Boolean=false)

class TelegramBookingIntegrationService(private val api:APIClient=APIClient()){
    suspend fun createLink(bookingID:String,headers:Map<String,String>,language:String):String{
        val response:TelegramLinkResponse=api.post("/api/telegram/link",TelegramLinkRequest(bookingID,language),headers,15)
        val url=response.linkUrl?.trim().orEmpty()
        require(response.ok && url.startsWith("https://t.me/")){"Invalid Telegram link"}
        return url
    }
    suspend fun isLinked(bookingID:String,headers:Map<String,String>):Boolean{
        val response:TelegramStatusResponse=api.get("/api/telegram/link",query=mapOf("bookingId" to bookingID),headers=headers,timeoutSeconds=12)
        return response.ok && response.linked
    }
}
