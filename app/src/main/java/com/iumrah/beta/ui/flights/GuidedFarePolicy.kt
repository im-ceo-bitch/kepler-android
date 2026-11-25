package com.iumrah.beta.ui.flights

import java.time.LocalDate

internal data class GuidedFareDay(val date: LocalDate, val price: Double?)
internal enum class GuidedSort { DATE, CHEAPEST, FASTEST }
internal data class GuidedFilters(val direct: Boolean = false, val airline: String = "", val sort: GuidedSort = GuidedSort.DATE) {
    fun accepts(stops: Int, code: String) = (!direct || stops == 0) && (airline.isEmpty() || airline.equals(code, true))
}
internal object GuidedFarePolicy {
    fun window(observations: List<Pair<LocalDate, Double?>>, selected: LocalDate, minimum: LocalDate, today: LocalDate = LocalDate.now()): List<GuidedFareDay> {
        val floor = maxOf(today, minimum)
        val anchor = maxOf(floor, selected)
        val start = maxOf(floor, anchor.minusDays(14))
        val end = anchor.plusDays(14)
        val fares = observations.filter { (date, price) -> date >= floor && price != null && price.isFinite() && price > 0 }
            .groupBy({ it.first }, { requireNotNull(it.second) }).mapValues { (_, values) -> values.minOrNull() }
        return generateSequence(start) { it.plusDays(1).takeIf { next -> next <= end } }.map { GuidedFareDay(it, fares[it]) }.toList()
    }
}
