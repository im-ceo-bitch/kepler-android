package com.iumrah.beta.domain.smart

import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.Airport
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

@Serializable
data class SmartPreferences(
    val origin: String = "TAS", val airport: Airport? = null,
    val month: String = YearMonth.now().toString(), val duration: Int = 7,
    val both: Boolean = true, val firstCity: String = "MED", val adults: Int = 2,
    val childAges: List<Int> = emptyList(), val tier: String = "standard",
) {
    val category get() = PackageTier.entries.firstOrNull { it.wireValue == tier } ?: PackageTier.STANDARD
    val children get() = childAges.count { it >= 2 }
    val infants get() = childAges.count { it < 2 }
    val arrival get() = if (!both || firstCity == "JED") "JED" else "MED"
    val returning get() = if (both && firstCity == "JED") "MED" else "JED"
    val roomCount get() = minOf(adults, maxOf(1, (adults + childAges.size + 1) / 2))
    fun valid(today: LocalDate = LocalDate.now()): Boolean = origin.matches(Regex("[A-Z]{3}")) &&
        origin !in setOf("JED", "MED") && (airport == null || airport.iata == origin) &&
        runCatching { YearMonth.parse(month) in YearMonth.from(today)..YearMonth.from(today).plusMonths(11) }.getOrDefault(false) &&
        duration in listOf(3, 7, 10, 14, 20, 25) && firstCity in setOf("JED", "MED") &&
        adults in 1..6 && adults + childAges.size <= 6 && childAges.all { it in 0..11 } && infants <= adults &&
        PackageTier.entries.any { it.wireValue == tier }
    fun trip(out: LocalDate, back: LocalDate) = TripDraft(origin = origin, originAirport = airport,
        arrivalAirport = if (arrival == "MED") SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
        departureDate = out, returnDate = back, adults = adults, children = children, infants = infants,
        rooms = roomCount, scope = if (both) JourneyScope.MAKKAH_AND_MADINAH else JourneyScope.MAKKAH_ONLY,
        hotelStars = category.primaryHotelStars, packageTier = category)
    fun occupancy(index: Int): Pair<Int, List<Int>> {
        require(index in 0 until roomCount)
        return (adults / roomCount + if (index < adults % roomCount) 1 else 0) to
            childAges.filterIndexed { i, _ -> i % roomCount == index }
    }
    fun matchesDays(departure: LocalDate, arrivalDay: LocalDate, returnDay: LocalDate): Boolean =
        YearMonth.from(departure).toString() == month &&
            kotlin.math.abs(ChronoUnit.DAYS.between(departure, returnDay) + 1 - duration) <= 4 &&
            ChronoUnit.DAYS.between(arrivalDay, returnDay) >= if (both) 2 else 1
}

@Serializable
data class SmartDraft(val preferences: SmartPreferences, val step: Int, val wasAssembled: Boolean = false)

object SmartRules {
    fun owner(authenticated: Boolean, id: String?): String? = if (!authenticated) "guest" else id?.takeIf { it.isNotBlank() }?.let { "account:$it" }
    fun steps(both: Boolean): List<Int> = (0..8).filter { both || it != 5 }
    fun resume(draft: SmartDraft, today: LocalDate = LocalDate.now()): SmartDraft? =
        draft.takeIf { it.preferences.copy(childAges = emptyList()).valid(today) &&
            it.preferences.adults + it.preferences.childAges.size <= 6 && it.preferences.childAges.all { age -> age in 0..11 } }
            ?.let { it.copy(step = if (!it.preferences.valid(today) && it.step >= 6) 6 else it.step.takeIf { s -> s in steps(it.preferences.both) } ?: 4) }
    fun starEligible(stars: Double?, category: PackageTier): Boolean = stars != null && stars.isFinite() && stars in 0.0..5.0 &&
        kotlin.math.floor(stars + 0.5).toInt() in category.selectableHotelStars
}
