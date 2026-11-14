package com.iumrah.beta.domain.trip

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class TripStayBreakdown(
    val totalNights: Int,
    val totalDays: Int,
    val makkahNights: Int,
    val madinahNights: Int,
)

data class TripStayWindow(
    val checkIn: LocalDate,
    val checkOut: LocalDate,
) {
    val nights: Int get() = max(1, ChronoUnit.DAYS.between(checkIn, checkOut).toInt())
}

data class TripStayWindows(
    val makkah: TripStayWindow,
    val madinah: TripStayWindow?,
)

object TripStayPlanner {
    fun breakdown(trip: TripDraft): TripStayBreakdown {
        val rawNights = ChronoUnit.DAYS.between(trip.hotelStayStartDate, trip.returnDate).toInt()
        val totalNights = max(1, rawNights)
        val totalDays = totalNights + 1

        if (trip.scope != JourneyScope.MAKKAH_AND_MADINAH || totalNights <= 1) {
            return TripStayBreakdown(
                totalNights = totalNights,
                totalDays = totalDays,
                makkahNights = totalNights,
                madinahNights = 0,
            )
        }

        if (trip.hotelFirstStayPolicy == true && totalNights >= 4) {
            val madinah = min(2, max(1, totalNights - 2))
            return TripStayBreakdown(totalNights, totalDays, totalNights - madinah, madinah)
        }
        val makkah = max(1, min(totalNights - 1, ceil(totalNights * 0.6).toInt()))
        val madinah = max(1, totalNights - makkah)
        return TripStayBreakdown(totalNights, totalDays, makkah, madinah)
    }

    fun windows(trip: TripDraft): TripStayWindows {
        val stay = breakdown(trip)
        val start = trip.hotelStayStartDate
        val end = trip.returnDate

        if (trip.scope != JourneyScope.MAKKAH_AND_MADINAH) {
            return TripStayWindows(TripStayWindow(start, end), null)
        }

        if (trip.arrivalAirport == SaudiArrivalAirport.MADINAH) {
            val madinahEnd = start.plusDays(stay.madinahNights.toLong())
            return TripStayWindows(
                makkah = TripStayWindow(madinahEnd, end),
                madinah = TripStayWindow(start, madinahEnd),
            )
        }

        val makkahEnd = start.plusDays(stay.makkahNights.toLong())
        return TripStayWindows(
            makkah = TripStayWindow(start, makkahEnd),
            madinah = TripStayWindow(makkahEnd, end),
        )
    }
}
