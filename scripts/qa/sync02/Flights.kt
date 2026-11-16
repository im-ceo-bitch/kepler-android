package com.iumrah.beta.data.flight
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.*
import java.time.LocalDate
class CuratedFlightRecommendationService(api: APIClient = APIClient()) {
    var resolver: suspend (TripDraft, CuratedPublishedFlightSelection) -> LiveFlightJourneyCandidate = { _, _ -> error("Unexpected resolution") }
    suspend fun resolvePublishedSelection(trip: TripDraft, selection: CuratedPublishedFlightSelection) = resolver(trip,selection)
    suspend fun load(trip: TripDraft, from: LocalDate = LocalDate.now(), days: Int = 365): List<CuratedFlightRecommendation> = emptyList()
}
class IgnavFlightInventoryProvider {
    suspend fun searchJourney(request: FlightJourneySearchRequest, datePairs: List<FlightJourneyDatePair>, onUpdate: suspend (List<LiveFlightJourneyCandidate>) -> Unit = {}): List<LiveFlightJourneyCandidate> = emptyList()
}
