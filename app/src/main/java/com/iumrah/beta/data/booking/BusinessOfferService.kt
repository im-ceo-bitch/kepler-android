package com.iumrah.beta.data.booking

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.models.booking.*

class BusinessOfferService(private val api: APIClient = APIClient()) {
    suspend fun load(token: String): BusinessOfferPayload = api.get(BusinessOfferPolicy.path(token))
    suspend fun accept(token: String, request: BusinessOfferAccept): BookingCreateResponse =
        api.post(BusinessOfferPolicy.path(token) + "/accept", request)
}
