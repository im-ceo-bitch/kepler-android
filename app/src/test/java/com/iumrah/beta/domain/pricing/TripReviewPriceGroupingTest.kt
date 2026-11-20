package com.iumrah.beta.domain.pricing

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class TripReviewPriceGroupingTest {
    private fun component(code: String, amount: String) = TripSummaryPublicPrice.Component(code, code, BigDecimal(amount))

    @Test fun iOS6GroupingPreservesAmountAndFirstAppearanceOrder() {
        val original = listOf(
            component("flight_outbound", "210.00"), component("makkah_hotel", "510.00"),
            component("flight_inbound", "200.00"),component("meals", "30.30"),
            component("meal_madinah", "19.70"),component("ziyarat_makkah", "100.00"),
            component("ziyarat_madinah", "100.00"),component("transfers", "400.00"),
            component("vip_transfer_upgrade", "100.00"),component("esim", "0.00"),
        )
        val grouped = TripReviewPriceGrouping.groups(original)
        assertEquals(listOf("flights","makkah_hotel","meals","ziyarats","transfer","esim"), grouped.map { it.key })
        assertEquals(BigDecimal("410.00"), grouped.first { it.key=="flights" }.amountUsd)
        assertEquals(BigDecimal("50.00"), grouped.first { it.key=="meals" }.amountUsd)
        assertEquals(BigDecimal("200.00"), grouped.first { it.key=="ziyarats" }.amountUsd)
        assertEquals(BigDecimal("500.00"), grouped.first { it.key=="transfer" }.amountUsd)
        assertEquals(original.fold(BigDecimal.ZERO) { sum, row -> sum + row.amountUsd },
            grouped.fold(BigDecimal.ZERO) { sum, row -> sum + row.amountUsd })
        assertEquals("flight_outbound", grouped.first().representative.code)
    }

    @Test fun groupingNeverRoundsOrChangesServerComponents() {
        val original = listOf(component("meals","0.001"),component("meal_makkah","0.002"),component("visa","300.000"))
        val grouped = TripReviewPriceGrouping.groups(original)
        assertEquals(BigDecimal("0.003"), grouped.first().amountUsd)
        assertEquals(BigDecimal("0.001"), original.first().amountUsd)
        assertEquals(BigDecimal("300.000"), grouped.last().amountUsd)
        assertEquals("non_flight", TripReviewPriceGrouping.key("non_flight"))
    }
}
