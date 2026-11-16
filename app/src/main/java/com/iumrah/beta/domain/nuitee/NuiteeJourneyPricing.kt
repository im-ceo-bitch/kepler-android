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
    val meals: com.iumrah.beta.models.nuitee.NuiteeMealChoices = com.iumrah.beta.models.nuitee.NuiteeMealChoices(),
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

/** Current SwiftUI LocalJourneySelection contract: only exact, complete room selections. */
object NuiteeBookingRules {
    fun tier(rooms: Collection<NuiteeRoomSelection>): PackageTier? {
        val stars = rooms.map { it.hotel.roundedStars }
        if (stars.isEmpty() || stars.any { it !in 1..5 }) return null
        return when (stars.maxOrNull()) { 1, 2 -> PackageTier.ECONOMY; 3, 4 -> PackageTier.STANDARD; 5 -> PackageTier.LUXURY; else -> null }
    }
    fun supplierMeals(rate: NuiteeRoomRate): com.iumrah.beta.models.nuitee.NuiteeMealChoices {
        rate.includedMeals?.let { return it }
        val board = rate.mealPlan.lowercase().trim()
        if (listOf("room only", "no meals", "without meals", "breakfast not included", "without breakfast", "no breakfast").any { board.contains(it) })
            return com.iumrah.beta.models.nuitee.NuiteeMealChoices()
        val full = board in listOf("fb", "ai") || board.contains("full board") || board.contains("all inclusive")
        val half = board == "hb" || board.contains("half board")
        return com.iumrah.beta.models.nuitee.NuiteeMealChoices(full || half || board == "bb" || board.contains("breakfast"), full, full || half || board.contains("dinner"))
    }
    fun bookingSelection(room: NuiteeRoomSelection): com.iumrah.beta.models.nuitee.BookingNuiteeRoomSelection {
        val included = supplierMeals(room.rate)
        val chosen = room.meals
        val bundled = room.hotel.roundedStars <= 3
        val requested = chosen.breakfast || chosen.lunch || chosen.dinner
        val meals = com.iumrah.beta.models.nuitee.NuiteeMealChoices(
            breakfast = included.breakfast || room.hotel.roundedStars >= 4 || (if (bundled) requested else chosen.breakfast),
            lunch = (!bundled || room.city == "makkah") && (included.lunch || (if (bundled) requested else chosen.lunch)),
            dinner = included.dinner || (if (bundled) requested else chosen.dinner),
        )
        return com.iumrah.beta.models.nuitee.BookingNuiteeRoomSelection(room.key, room.city, room.hotel.id, room.rate.offerId,
            room.rate.roomId, room.checkin.toString(), room.checkout.toString(), room.adults, room.childrenAges, meals,
            if (bundled) requested else null)
    }
    fun complete(trip: TripDraft, draft: NuiteeJourneyDraft): Boolean {
        if (trip.rooms !in 1..trip.adults || draft.pending.isNotEmpty() || tier(draft.rooms.values) == null) return false
        val windows = TripStayPlanner.windows(trip)
        val expectedKeys = draft.requiredCities(trip).flatMap { city -> (0 until trip.rooms).map { "$city:$it" } }.toSet()
        if (draft.rooms.keys != expectedKeys) return false
        return draft.requiredCities(trip).all { city ->
            val window = (if (city == "madinah") windows.madinah else windows.makkah) ?: return@all false
            val selected = (0 until trip.rooms).map { draft.rooms.getValue("$city:$it") }
            selected.map { it.hotel.id }.distinct().size == 1 &&
                selected.sumOf { it.adults } == trip.adults && selected.sumOf { it.childrenAges.size } == trip.children + trip.infants &&
                selected.all { room -> room.city == city && room.key == "$city:${room.roomIndex}" && room.roomIndex in 0 until trip.rooms &&
                    room.adults == trip.adults / trip.rooms + (if (room.roomIndex < trip.adults % trip.rooms) 1 else 0) &&
                    room.checkin == window.checkIn && room.checkout == window.checkOut &&
                    NuiteeRules.validStay(room.checkin, room.checkout, room.adults, room.childrenAges) && NuiteeRules.validRoomRate(room.rate) }
        }
    }
}
