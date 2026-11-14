package com.iumrah.beta.ui.flights

import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.domain.nuitee.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.domain.umrahflow.*
import com.iumrah.beta.models.flight.*
import com.iumrah.beta.models.nuitee.*
import java.time.LocalDate
import java.math.BigDecimal

/** These tests compile the REAL production Kotlin domain/rules files with JVM-only DTO stubs. */
object Stage10JvmRegression {
    private var passed = 0
    private fun checkThat(name: String, condition: Boolean) {
        if (!condition) error("STAGE10 FAILED: $name")
        passed++
    }

    @JvmStatic fun main(args: Array<String>) {
        val day = LocalDate.of(2026,12,3)
        val ret = day.plusDays(7)
        var ritual = UmrahFlowProgress()
        checkThat("cannot pre-count Tawaf", ritual.completeTawafTurn() == ritual)
        checkThat("cannot pre-count Sa'i", ritual.completeSaiLeg() == ritual)
        ritual = ritual.advance()
        repeat(6) { ritual = ritual.completeTawafTurn() }
        checkThat("six Tawaf circuits cannot advance", ritual.advance() == ritual)
        ritual = ritual.completeTawafTurn().completeTawafTurn()
        checkThat("Tawaf saturates at seven", ritual.tawafCompleted == 7)
        ritual = ritual.advance().advance()
        checkThat("Sa'i comes after post-Tawaf", ritual.phase == UmrahPhase.SAI)
        repeat(6) { ritual = ritual.completeSaiLeg() }
        checkThat("six Sa'i legs cannot advance", ritual.advance() == ritual)
        ritual = ritual.completeSaiLeg().completeSaiLeg().advance().advance()
        checkThat("Sa'i saturates at seven", ritual.saiCompleted == 7)
        checkThat("ritual completes", ritual.phase == UmrahPhase.COMPLETED)
        checkThat("completed ritual never advances", !ritual.canAdvance && ritual.advance() == ritual)
        val audio = "https://iumrah-backend.helloiumrah.workers.dev/api/services/advisor/audio/ru/tawaf_1"
        checkThat("public audio accepted", UmrahFlowAudioUrlPolicy.isAllowed(audio))
        checkThat("no plain HTTP audio", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("https:","http:")))
        checkThat("no fake audio host", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("workers.dev", "workers.dev.evil.org")))
        checkThat("no private bucket", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("/advisor/audio/", "/advisor/translations/")))
        checkThat("no userinfo", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("https://", "https://me@")))
        checkThat("no nonstandard port", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("workers.dev/", "workers.dev:8443/")))
        checkThat("no audio query", !UmrahFlowAudioUrlPolicy.isAllowed("$audio?token=secret"))
        checkThat("no traversal", !UmrahFlowAudioUrlPolicy.isAllowed(audio.replace("audio/ru/tawaf", "audio/../tawaf")))

        val hotel = NuiteeHotel("1", "Royal Hotel", "makkah", address = "Jabal Omar",
            stars=4.5, minimumRate=NuiteeMinimumRate("1", 350.0), rateStatus="fresh")
        val rate = NuiteeRoomRate("offer", "room", price=350.0)
        checkThat("verified Nuitee rate", NuiteeRules.validRoomRate(rate))
        checkThat("rejected empty room ID", !NuiteeRules.validRoomRate(rate.copy(roomId="")))
        checkThat("rejected invalid currency", !NuiteeRules.validRoomRate(rate.copy(currency="EUR")))
        checkThat("rejected nonfinite price", !NuiteeRules.validRoomRate(rate.copy(price=Double.NaN)))
        checkThat("rejected negative price", !NuiteeRules.validRoomRate(rate.copy(price=-50.0)))
        checkThat("rejected stale cache", NuiteeRules.listedRate(hotel, stale=true) == null)
        checkThat("rejected provider error", NuiteeRules.listedRate(hotel.copy(rateStatus="provider_error"), stale=false) == null)
        checkThat("fresh rate visible", NuiteeRules.listedRate(hotel, stale=false) == 350.0)
        checkThat("hotel name search works", NuiteeRules.filterHotels(listOf(hotel), 5, "royal jabal").size == 1)
        checkThat("date check rejects same-day", !NuiteeRules.validStay(day,day,2,emptyList()))
        checkThat("child age cannot be negative", !NuiteeRules.validStay(day,ret,2,listOf(-1)))
        checkThat("child age max=17", NuiteeRules.validStay(day,ret,2,listOf(17)))
        checkThat("offers must match", !NuiteeRules.sameOffer(rate,rate.copy(offerId="different")))

        val trip = TripDraft(origin="TAS", departureDate=day, returnDate=ret,
            arrivalAirport=SaudiArrivalAirport.JEDDAH, scope=JourneyScope.MAKKAH_ONLY, adults=2)
        val out=FlightDiscoveryOffer("o","TAS","JED",250.0, departureAt="${day}T08:00:00Z")
        val back=FlightDiscoveryOffer("r","JED","TAS",275.0, departureAt="${ret}T08:00:00Z")
        val outChoice=GuidedFlightChoice.Data(out)
        val backChoice=GuidedFlightChoice.Data(back)
        checkThat("API flights pair", GuidedFlightRules.compatible(outChoice,backChoice))
        checkThat("reverse flights not pair", !GuidedFlightRules.compatible(backChoice,outChoice))
        checkThat("invalid return day rejected", !GuidedFlightRules.compatible(outChoice,GuidedFlightChoice.Data(back.copy(departureAt="${day}T08:00:00Z"))))
        val rec=CuratedFlightRecommendation(id="p",outboundDate=day.toString(), cabinClass="economy",
            observedAt="2026-11-22T12:00:00Z", nonstop=true, recommendationLabel="published",
            outbound=CuratedFlightRecommendation.Leg("UzAir","HY100","HY","TAS","JED",
                "${day}T09:00:00Z","${day}T13:00:00Z",240,0,"economy"))
        val published=GuidedFlightChoice.Published(rec,false)
        checkThat("published + API never mixed", !GuidedFlightRules.compatible(published,backChoice))
        checkThat("published price is not fabricated", published.priceUsd == null)
        checkThat("bad airfare excluded", GuidedFlightRules.options(trip, emptyList(),
            listOf(out.copy(price=0.0),out), emptyList(),false,null,today=day.minusDays(1)).size == 1)
        checkThat("valid outbound listed", GuidedFlightRules.options(trip, emptyList(),
            listOf(out),emptyList(),false,null,today=day.minusDays(1)).size == 1)
        checkThat("invalid route filtered", GuidedFlightRules.options(trip, emptyList(),
            listOf(out.copy(destination="RUH")),emptyList(),false,null,today=day.minusDays(1)).isEmpty())
        checkThat("no return without outbound", GuidedFlightRules.options(trip,emptyList(),
            emptyList(),listOf(back),true,null,today=day.minusDays(1)).isEmpty())
        val returnOptions=GuidedFlightRules.options(trip, emptyList(), emptyList(), listOf(back),
            true, outChoice, today=day.minusDays(1))
        checkThat("valid return option", returnOptions.size == 1 && GuidedFlightRules.compatible(outChoice,returnOptions.single()))

        val room = NuiteeRoomSelection("makkah:0","makkah",0,hotel,rate,day,ret,2,emptyList())
        val flights = listOf(NuiteeFlightSnapshot("o","TAS","JED","UzAir","HY100","HY",210,0,
            "${day}T08:00:00Z",null,250.0),
            NuiteeFlightSnapshot("r","JED","TAS","UzAir","HY101","HY",210,0,
            "${ret}T08:00:00Z",null,275.0))
        val draft=NuiteeJourneyDraft(flights, mapOf(room.key to room))
        val quote=NuiteeJourneyPricing.estimate(trip,draft)
        checkThat("complete draft gives estimate", quote != null && quote.totalUsd > BigDecimal.ZERO)
        checkThat("estimate line reconciliation", quote!!.totalUsd == quote.lines.fold(BigDecimal.ZERO){acc,item->acc+item.usd})
        checkThat("no child flight quote invention", NuiteeJourneyPricing.estimate(trip.copy(children=1),draft)==null)
        checkThat("no infant flight quote invention", NuiteeJourneyPricing.estimate(trip.copy(infants=1),draft)==null)
        checkThat("no guessed published flight fare", NuiteeJourneyPricing.estimate(trip,draft.copy(flights=listOf(flights[0].copy(adultFareUSD=null),flights[1])))==null)
        checkThat("invalid room offer never priced", NuiteeJourneyPricing.estimate(trip,draft.copy(rooms=mapOf(room.key to room.copy(rate=rate.copy(currency="EUR")))))==null)
        checkThat("missing room cannot complete", !draft.copy(rooms=emptyMap()).roomsComplete(trip))
        println("STAGE10 JVM RULES PASS ($passed assertions; real Kotlin domain code, minimal external DTO stubs)")
    }
}
