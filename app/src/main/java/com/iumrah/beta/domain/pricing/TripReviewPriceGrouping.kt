package com.iumrah.beta.domain.pricing

import java.math.BigDecimal

/** Port of iOS FlightModels.TripReviewPriceGrouping. Only presentation changes;
 * retained server components remain the authority for quotes, fees and booking. */
object TripReviewPriceGrouping {
    data class Group(val key: String, val representative: TripSummaryPublicPrice.Component, val amountUsd: BigDecimal)

    fun key(code: String): String = when {
        code.startsWith("flight_") -> "flights"
        code == "meals" || code.startsWith("meal_") -> "meals"
        code.startsWith("ziyarat_") -> "ziyarats"
        code == "transfers" || code == "vip_transfer_upgrade" -> "transfer"
        else -> code
    }

    fun groups(components: List<TripSummaryPublicPrice.Component>): List<Group> {
        val result = linkedMapOf<String, Group>()
        components.forEach { component ->
            val groupKey = key(component.code)
            val previous = result[groupKey]
            result[groupKey] = if (previous == null) Group(groupKey, component, component.amountUsd)
                else previous.copy(amountUsd = previous.amountUsd.add(component.amountUsd))
        }
        return result.values.toList()
    }
}
