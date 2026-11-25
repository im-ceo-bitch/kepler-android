package com.iumrah.beta.data.flight
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import java.time.LocalDate
class CuratedFlightRecommendationService(api:APIClient) { suspend fun load(trip:TripDraft,from:LocalDate,days:Int):List<CuratedFlightRecommendation> = error("offline test") }
