package com.iumrah.beta.data.care

import com.iumrah.beta.core.network.APIClient
import kotlinx.serialization.Serializable

@Serializable
data class IumrahCarePackageRequest(
    val locale:String, val firstName:String, val lastName:String, val phone:String, val telegram:String,
    val accountID:String?=null, val originCode:String, val timingMode:String, val preferredMonth:String?=null,
    val flexibleWindowDays:Int?=null, val exactStartDate:String?=null, val exactEndDate:String?=null,
    val adults:Int, val children:Int, val infants:Int, val rooms:Int, val scope:String, val firstSaudiCity:String?=null,
    val priority:String, val hotelClass:Int, val transferPreference:String, val directFlightsPreferred:Boolean,
    val checkedBaggagePreferred:Boolean, val includeZiyarat:Boolean, val includeESIM:Boolean, val guidePreference:String,
    val budgetUSD:Int?=null, val notes:String,
    val configuration: NuiteeCareConfiguration? = null,
)
@Serializable data class IumrahCarePackageRequestResponse(val ok:Boolean=false,val requestID:String,val status:String="",val createdAt:String="",val responseDueAt:String="")
class IumrahCareRequestService(private val api:APIClient=APIClient()){
    suspend fun submit(request:IumrahCarePackageRequest,token:String?):IumrahCarePackageRequestResponse{
        val headers=token?.trim()?.takeIf{it.isNotBlank()}?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
        return api.post("/api/package/care-requests",request,headers,30)
    }
}
