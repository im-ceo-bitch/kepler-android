package com.iumrah.beta.domain.trip

import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.models.flight.FlightSearchFilters
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

enum class PackageFlightPath(val wireValue: String) {
    PUBLISHED_DIRECT("publishedDirect"),
    AVIASALES_SELECTED("aviasalesSelected"),
    FLEXIBLE_DATES("flexibleDates"),
    WEEKEND("weekend"),
}

enum class PackageTier(val wireValue: String) {
    ECONOMY("economy"), STANDARD("standard"), COMFORT("comfort"), LUXURY("luxury");

    val primaryHotelStars: Int get() = when (this) {
        ECONOMY -> 2
        STANDARD -> 3
        COMFORT -> 4
        LUXURY -> 5
    }

    val selectableHotelStars: List<Int> get() = when (this) {
        ECONOMY -> listOf(2, 1)
        STANDARD -> listOf(3)
        COMFORT -> listOf(4)
        LUXURY -> listOf(5)
    }

    val optionalMealUnitPriceUsd: Int? get() = when (this) {
        ECONOMY, STANDARD -> null
        COMFORT -> 30
        LUXURY -> 50
    }
}

enum class DateFlexibility(val wireValue: String) {
    EXACT("exact"), PLUS_MINUS_ONE("plusMinusOne"), PLUS_MINUS_TWO("plusMinusTwo"), WEEKEND("weekend");
    val isFlexibleDayRange: Boolean get() = this == PLUS_MINUS_ONE || this == PLUS_MINUS_TWO
    val isWeeklyDiscovery: Boolean get() = false
}

enum class JourneyScope(val wireValue: String) { MAKKAH_ONLY("makkahOnly"), MAKKAH_AND_MADINAH("makkahAndMadinah") }
enum class SaudiArrivalAirport(val iata: String) { JEDDAH("JED"), MADINAH("MED") }
enum class FlightTripType(val wireValue: String) { ROUND_TRIP("roundTrip"), ONE_WAY("oneWay") }

@Serializable
data class PackageMealSelection(
    val makkahLunch: Boolean = false,
    val makkahDinner: Boolean = false,
    val madinahDinner: Boolean = false,
)

@Serializable
enum class FlightFareScope(val wireValue: String) {
    PER_PASSENGER("perPassenger"), TOTAL_PARTY("totalParty"), UNKNOWN("unknown");
    companion object {
        fun fromWire(value: String): FlightFareScope = entries.firstOrNull { it.wireValue.equals(value, true) } ?: UNKNOWN
    }
}

@Serializable
data class IumrahGuidePreference(val memberID: String, val displayName: String)

@Serializable
data class TripDraft(
    val origin: String = "TAS",
    val originAirport: Airport? = null,
    val arrivalAirport: SaudiArrivalAirport = SaudiArrivalAirport.JEDDAH,
    @Serializable(with = com.iumrah.beta.core.serialization.LocalDateIsoSerializer::class) val departureDate: LocalDate = LocalDate.now().plusDays(21),
    @Serializable(with = com.iumrah.beta.core.serialization.LocalDateIsoSerializer::class) val saudiArrivalDate: LocalDate? = null,
    @Serializable(with = com.iumrah.beta.core.serialization.LocalDateIsoSerializer::class) val returnDate: LocalDate = LocalDate.now().plusDays(28),
    val flexibility: DateFlexibility = DateFlexibility.EXACT,
    val adults: Int = 2,
    val children: Int = 0,
    val infants: Int = 0,
    val rooms: Int = 1,
    val hotelStars: Int = 3,
    val packageTier: PackageTier = PackageTier.STANDARD,
    val mealSelection: PackageMealSelection? = null,
    val scope: JourneyScope = JourneyScope.MAKKAH_AND_MADINAH,
    /** Hotel First alone uses the fixed storefront stay policy. Nullable keeps
     * parity with old iOS/Android drafts where the field did not exist. */
    val hotelFirstStayPolicy: Boolean? = null,
    val flightFilters: FlightSearchFilters? = null,
    val flightTripType: FlightTripType? = null,
    val selectedReturnAirport: SaudiArrivalAirport? = null,
    val guidePreference: IumrahGuidePreference? = null,
) {
    val travelerCount: Int get() = adults + children + infants
    val hotelStayStartDate: LocalDate get() = saudiArrivalDate ?: departureDate
    val effectiveFlightFilters: FlightSearchFilters get() = flightFilters ?: FlightSearchFilters()
    val effectiveMealSelection: PackageMealSelection get() = mealSelection ?: PackageMealSelection()
    val resolvedFlightTripType: FlightTripType get() = flightTripType ?: FlightTripType.ROUND_TRIP
    val isRoundTripFlight: Boolean get() = resolvedFlightTripType == FlightTripType.ROUND_TRIP
    val isWeekendUmrah: Boolean get() = flexibility == DateFlexibility.WEEKEND

    val originCode: String get() = (originAirport?.iata ?: origin).trim().uppercase()
    val outboundDestinationCode: String get() = if (isWeekendUmrah || scope != JourneyScope.MAKKAH_AND_MADINAH) "JED" else arrivalAirport.iata
    val returnOriginCode: String get() = when {
        isWeekendUmrah || scope != JourneyScope.MAKKAH_AND_MADINAH -> "JED"
        selectedReturnAirport != null -> selectedReturnAirport.iata
        arrivalAirport == SaudiArrivalAirport.MADINAH -> "JED"
        else -> "MED"
    }

    fun withFlexibility(value: DateFlexibility, today: LocalDate = LocalDate.now()): TripDraft {
        val normalized = if (value == DateFlexibility.PLUS_MINUS_ONE) DateFlexibility.PLUS_MINUS_TWO else value
        return if (normalized == DateFlexibility.WEEKEND) copy(flexibility = normalized).applyWeekendWindow(today) else copy(flexibility = normalized)
    }

    fun applyWeekendWindow(today: LocalDate = LocalDate.now()): TripDraft {
        var reference = if (departureDate.isBefore(today)) today else departureDate
        var friday = reference
        while (friday.dayOfWeek != DayOfWeek.FRIDAY) friday = friday.plusDays(1)
        if ((reference.dayOfWeek == DayOfWeek.SATURDAY || reference.dayOfWeek == DayOfWeek.SUNDAY)) {
            val previousFriday = reference.minusDays(if (reference.dayOfWeek == DayOfWeek.SATURDAY) 1 else 2)
            if (!previousFriday.isBefore(today)) friday = previousFriday
        }
        return copy(
            flexibility = DateFlexibility.WEEKEND,
            scope = JourneyScope.MAKKAH_ONLY,
            arrivalAirport = SaudiArrivalAirport.JEDDAH,
            departureDate = friday,
            saudiArrivalDate = null,
            returnDate = friday.plusDays(3),
        )
    }

    val canContinue: Boolean get() = originCode.matches(Regex("^[A-Z]{3}$")) && adults > 0 && travelerCount <= 9 && rooms > 0 && returnDate.isAfter(departureDate)
}
