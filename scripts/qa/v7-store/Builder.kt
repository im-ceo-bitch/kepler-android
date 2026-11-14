package com.iumrah.beta.domain.booking
import com.iumrah.beta.models.booking.*
object BookingDraftBuilder {
    fun make(journey:com.iumrah.beta.domain.journey.JourneyState,quote:com.iumrah.beta.domain.pricing.PackageQuote,language:com.iumrah.beta.core.settings.AppLanguage,profile:BookingPilgrimProfile?):BookingCreateEnvelope =
        BookingCreateEnvelope("ru", BookingDraftRequest(
            "economy", 100.0, 100.0,
            BookingInput("Tashkent","TAS","JED","economy","economy","2026-12-01","2026-12-05",0,"economy",false,"round_trip",BookingTravelers(1,0,0,1)),
            BookingRoute("TAS","JED","JED"), BookingStay(5,4,"2026-12-01","2026-12-05",4), BookingSelection("flight","hotel"),
            BookingCustomization(false,"",false,false,false,false), emptyList(), BookingHotelNames("h",""),"flight",profile,
        ), quote.quoteProof)
}
