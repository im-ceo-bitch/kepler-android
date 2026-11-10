package com.iumrah.beta.domain.nuitee

import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.domain.trip.HaramainFareClass
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeRoomRate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Explicitly tentative client-side estimate, never a signed server booking quote. */
data class NuiteeFlightSnapshot(
    val id: String, val origin: String, val destination: String,
    val airline: String, val flightNumber: String, val airlineCode: String,
    val durationMinutes: Int, val stops: Int, val departureAt: String,
    val arrivalAt: String?, val adultFareUSD: Double?,
)

data class NuiteeRoomSelection(
    val key: String, val city: String, val roomIndex: Int,
    val hotel: NuiteeHotel, val rate: NuiteeRoomRate,
    val checkin: LocalDate, val checkout: LocalDate,
    val adults: Int, val childrenAges: List<Int>,
)

data class NuiteePendingHotel(
    val key: String, val city: String, val roomIndex: Int,
    val hotel: NuiteeHotel, val checkin: LocalDate, val checkout: LocalDate,
    val adults: Int, val childrenAges: List<Int>,
)

data class NuiteeJourneyDraft(
    val flights: List<NuiteeFlightSnapshot> = emptyList(),
    val rooms: Map<String, NuiteeRoomSelection> = emptyMap(),
    val pending: Map<String, NuiteePendingHotel> = emptyMap(),
) {
    fun requiredCities(trip: TripDraft): List<String> =
        if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) listOf("makkah", "madinah") else listOf("makkah")

    fun roomsComplete(trip: TripDraft): Boolean = requiredCities(trip).all { city ->
        (0 until trip.rooms).all { index -> "$city:$index" in rooms || "$city:$index" in pending }
    }
}

data class NuiteePriceLine(val id: String, val title: String, val usd: BigDecimal)
data class NuiteeEstimate(val lines: List<NuiteePriceLine>, val incompleteHotels: Boolean) {
    val totalUsd: BigDecimal get() = lines.fold(BigDecimal.ZERO) { total, line -> total + line.usd }
}

/** Same rules as iOS LocalJourneySelection; guards against unknown child/infant flight pricing. */
object NuiteeJourneyPricing {
    private fun usd(value: Double): BigDecimal = BigDecimal.valueOf(value)

    fun estimate(trip: TripDraft, draft: NuiteeJourneyDraft,
                 vehicle: TransferVehicleKind? = null, trainSelected: Boolean = false,
                 trainTicketCount: Int = 0, trainClass: HaramainFareClass = HaramainFareClass.ECONOMY): NuiteeEstimate? {
        if (!draft.roomsComplete(trip) || draft.flights.size != 2 || trip.adults <= 0 ||
            trip.children > 0 || trip.infants > 0 || draft.flights.any { (it.adultFareUSD ?: 0.0) <= 0.0 || it.adultFareUSD?.isFinite() != true }) return null
        val people = BigDecimal(trip.adults)
        val lines = mutableListOf<NuiteePriceLine>()
        draft.flights.forEachIndexed { index, flight ->
            lines += NuiteePriceLine("flight:$index", "${flight.origin} → ${flight.destination}", usd(flight.adultFareUSD!!) * people)
        }
        for ((key, selection) in draft.rooms.toSortedMap()) {
            if (!NuiteeRules.validRoomRate(selection.rate)) return null
            lines += NuiteePriceLine(key, "${selection.hotel.name} · ${selection.rate.roomName}", usd(selection.rate.price))
        }
        val both = trip.scope == JourneyScope.MAKKAH_AND_MADINAH
        val stay = TripStayPlanner.breakdown(trip)
        lines += NuiteePriceLine("visa", "Визы", BigDecimal(120) * people)
        lines += NuiteePriceLine("transfer", "Трансфер", BigDecimal(300))
        lines += NuiteePriceLine("guide", "Сопровождение", BigDecimal(if (both) 300 else 100))
        lines += NuiteePriceLine("ziyarat", "Зияраты", BigDecimal(if (both) 200 else 100))
        val diners = people
        val mealAmount = if (trip.packageTier == PackageTier.ECONOMY || trip.packageTier == PackageTier.STANDARD) {
            BigDecimal(15 * stay.totalDays) * diners
        } else {
            val unit = if (trip.packageTier == PackageTier.LUXURY) BigDecimal(50) else BigDecimal(30)
            val meals = trip.effectiveMealSelection
            val mealsCount = (if (meals.makkahLunch) stay.makkahNights else 0) +
                (if (meals.makkahDinner) stay.makkahNights else 0) +
                (if (both && meals.madinahDinner) stay.madinahNights else 0)
            unit * BigDecimal(mealsCount) * diners
        }
        lines += NuiteePriceLine("meals", "Питание и обслуживание", mealAmount)
        val cost = lines.fold(BigDecimal.ZERO) { total, line -> total + line.usd }
        val factor = if (trip.packageTier == PackageTier.LUXURY) BigDecimal("1.35") else BigDecimal("1.25")
        val selling = cost.multiply(factor).divide(BigDecimal("0.98"), 12, RoundingMode.HALF_UP)
        val each = selling.divide(people, 12, RoundingMode.HALF_UP).divide(BigDecimal(5), 10, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE).multiply(BigDecimal(5))
        val rounded = each * people
        lines += NuiteePriceLine("organization", "Организация, комиссия и округление", rounded - cost)
        val vehicleUpgrade = vehicle?.publicUpgradeUsd(trip.scope) ?: 0
        if (vehicleUpgrade > 0) lines += NuiteePriceLine("vehicle", "Класс трансфера", BigDecimal(vehicleUpgrade))
        if (both && trainSelected && trainTicketCount > 0) {
            lines += NuiteePriceLine("train", "Haramain", BigDecimal(trainClass.publicSeatPriceUsd * trainTicketCount))
        }
        return NuiteeEstimate(lines, draft.pending.isNotEmpty())
    }
}
