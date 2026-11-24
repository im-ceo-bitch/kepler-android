package com.iumrah.beta.domain.trip

import java.time.LocalDate

data class TripDraft(
    val adults: Int = 2, val children: Int = 0, val infants: Int = 0,
    val rooms: Int = 1, val departureDate: LocalDate = LocalDate.now().plusDays(14),
    val returnDate: LocalDate = LocalDate.now().plusDays(21),
)
