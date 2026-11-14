package com.iumrah.beta.ui.flights

import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.flight.FlightCabinClass
import java.time.LocalDate
import kotlin.math.abs

/**
 * Matches iOS IumrahGuidedFlightSelectionView: one coherent departure/return
 * selection built from published inventory and Data API observations.
 * Source compatibility is a hard rule: mixing providers cannot produce a quote.
 */
internal sealed interface GuidedFlightChoice {
    val id: String
    val source: Source
    val origin: String
    val destination: String
    val airlineCode: String
    val airlineName: String
    val flightNumber: String
    val departureAt: String
    val arrivalAt: String?
    val stops: Int
    val durationMinutes: Int
    val priceUsd: Double?
    val publishedPair: Boolean

    enum class Source { PUBLISHED, DATA_API }

    data class Published(val recommendation: CuratedFlightRecommendation, val isReturn: Boolean) : GuidedFlightChoice {
        private val leg: CuratedFlightRecommendation.Leg get() =
            if (isReturn) recommendation.inbound ?: recommendation.outbound else recommendation.outbound
        override val id: String get() = "pub:${recommendation.id}:$isReturn"
        override val source = Source.PUBLISHED
        override val origin get() = leg.origin.uppercase()
        override val destination get() = leg.destination.uppercase()
        override val airlineCode get() = leg.airlineCode
        override val airlineName get() = leg.airline
        override val flightNumber get() = leg.flightNumber
        override val departureAt get() = leg.departureAt
        override val arrivalAt get() = leg.arrivalAt
        override val stops get() = leg.stops
        override val durationMinutes get() = leg.durationMinutes
        override val priceUsd: Double? = null // Published fares are returned by the server quote, not this catalogue.
        override val publishedPair get() = recommendation.inbound != null
    }

    data class Data(val offer: FlightDiscoveryOffer) : GuidedFlightChoice {
        override val id get() = "api:${offer.id}"
        override val source = Source.DATA_API
        override val origin get() = offer.origin.uppercase()
        override val destination get() = offer.destination.uppercase()
        override val airlineCode get() = offer.airlineCode
        override val airlineName get() = offer.airlineCode.ifBlank { "Airline" }
        override val flightNumber get() = offer.flightNumber
        override val departureAt get() = offer.departureAt
        override val arrivalAt: String? get() = offer.arrivalAt ?: offer.outboundSegments?.lastOrNull()?.arrivalAt
        override val stops get() = offer.transfers
        override val durationMinutes get() = offer.durationMinutes
        override val priceUsd get() = offer.price.takeIf { it.isFinite() && it > 0 }
        override val publishedPair = false
    }
}

internal object GuidedFlightRules {
    fun departureDay(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

    private fun dedupe(value: GuidedFlightChoice): String {
        val number = value.flightNumber.uppercase().filter(Char::isLetterOrDigit)
        return listOf(value.origin, value.destination, value.airlineCode.uppercase(),
            number, value.departureAt.take(10), if (number.isBlank()) value.departureAt.take(16) else "")
            .joinToString("|")
    }

    /** iOS uses ±3 or ±4 days and respects the selected cabin class. */
    fun options(
        trip: TripDraft,
        published: List<CuratedFlightRecommendation>,
        dataOutbound: List<FlightDiscoveryOffer>,
        dataReturn: List<FlightDiscoveryOffer>,
        returnStep: Boolean,
        selectedOutbound: GuidedFlightChoice?,
        flexDays: Int = 3,
        today: LocalDate = LocalDate.now(),
    ): List<GuidedFlightChoice> {
        if (returnStep && selectedOutbound == null) return emptyList()
        val target = if (returnStep) trip.returnDate else trip.departureDate
        val targetOrigin = if (returnStep) trip.returnOriginCode else trip.originCode
        val targetDestination = if (returnStep) trip.originCode else trip.outboundDestinationCode
        val raw = mutableListOf<GuidedFlightChoice>()
        val cabin = trip.effectiveFlightFilters.cabinClass.wireValue
        for (row in published) {
            if (!row.nonstop || !row.cabinClass.trim().lowercase().replace(' ', '_').equals(cabin, true)) continue
            val leg = row.outbound
            if (!returnStep) {
                if (!leg.origin.equals(targetOrigin, true) || !leg.destination.equals(targetDestination, true)) continue
                val fixed = row.inbound
                if (fixed != null) {
                    if (!fixed.origin.equals(trip.returnOriginCode, true) ||
                        !fixed.destination.equals(trip.originCode, true)) continue
                    val fixedDay = departureDay(fixed.departureAt) ?: continue
                    if (abs(java.time.temporal.ChronoUnit.DAYS.between(trip.returnDate, fixedDay)) > flexDays) continue
                }
                raw.add(GuidedFlightChoice.Published(row, false))
            } else {
                when {
                    row.inbound != null && selectedOutbound is GuidedFlightChoice.Published &&
                        selectedOutbound.recommendation.inbound != null &&
                        row.outbound.flightNumber == selectedOutbound.recommendation.outbound.flightNumber &&
                        row.outbound.departureAt == selectedOutbound.recommendation.outbound.departureAt &&
                        row.inbound.origin.equals(targetOrigin, true) &&
                        row.inbound.destination.equals(targetDestination, true) ->
                        raw.add(GuidedFlightChoice.Published(row, true))
                    row.inbound == null && row.effectiveJourneyRole == "return" &&
                        leg.origin.equals(targetOrigin, true) && leg.destination.equals(targetDestination, true) ->
                        raw.add(GuidedFlightChoice.Published(row, true))
                }
            }
        }
        if (trip.effectiveFlightFilters.cabinClass == FlightCabinClass.ECONOMY) {
            val rows = if (returnStep) dataReturn else dataOutbound
            rows.asSequence().filter { row ->
                !row.isRoundTrip && row.origin.equals(targetOrigin, true) &&
                    row.destination.equals(targetDestination, true) && row.durationMinutes > 0 &&
                    row.transfers >= 0 && row.price.isFinite() && row.price > 0
            }.forEach { raw.add(GuidedFlightChoice.Data(it)) }
        }
        val outgoing = raw.asSequence().filter { value ->
            if (returnStep && value.source != selectedOutbound?.source) return@filter false
            if (returnStep && selectedOutbound is GuidedFlightChoice.Published) {
                if (selectedOutbound.publishedPair &&
                    (value !is GuidedFlightChoice.Published ||
                        value.recommendation.id != selectedOutbound.recommendation.id)) return@filter false
                if (!selectedOutbound.publishedPair && value.publishedPair) return@filter false
            }
            val day = departureDay(value.departureAt) ?: return@filter false
            val outDay = selectedOutbound?.let { departureDay(it.departureAt) }
            !day.isBefore(today) && abs(java.time.temporal.ChronoUnit.DAYS.between(target, day)) <= flexDays &&
                (!returnStep || outDay == null || day.isAfter(outDay))
        }.sortedWith(compareBy<GuidedFlightChoice> {
            if (it.source == GuidedFlightChoice.Source.PUBLISHED) 0 else 1
        }.thenBy { if (it.publishedPair) 0 else 1 }.thenBy { it.id }).distinctBy(::dedupe).toList()
        return outgoing.sortedWith(compareBy<GuidedFlightChoice> { value ->
            val date = departureDay(value.departureAt) ?: target
            when { date == target && value.stops == 0 -> 0
                date == target -> 1
                else -> 2 }
        }.thenBy { value ->
            abs(java.time.temporal.ChronoUnit.DAYS.between(target, departureDay(value.departureAt) ?: target))
        }.thenBy { it.departureAt }.thenBy { it.id })
    }

    /** No cross-provider combinations, and no mixing paired published itineraries with one-way tickets. */
    fun compatible(outbound: GuidedFlightChoice?, inbound: GuidedFlightChoice?): Boolean {
        if (outbound == null || inbound == null || outbound.source != inbound.source) return false
        val outDay = departureDay(outbound.departureAt) ?: return false
        val inDay = departureDay(inbound.departureAt) ?: return false
        if (!inDay.isAfter(outDay) || outbound.origin != inbound.destination) return false
        if (outbound is GuidedFlightChoice.Published && inbound is GuidedFlightChoice.Published) {
            return if (outbound.publishedPair) inbound.publishedPair &&
                outbound.recommendation.id == inbound.recommendation.id &&
                outbound.recommendation.outbound.flightNumber == inbound.recommendation.outbound.flightNumber &&
                outbound.recommendation.outbound.departureAt == inbound.recommendation.outbound.departureAt
            else !inbound.publishedPair && outbound.recommendation.id != inbound.recommendation.id
        }
        return outbound is GuidedFlightChoice.Data && inbound is GuidedFlightChoice.Data
    }
}
