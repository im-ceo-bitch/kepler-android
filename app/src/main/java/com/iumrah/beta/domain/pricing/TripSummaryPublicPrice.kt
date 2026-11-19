package com.iumrah.beta.domain.pricing

import com.iumrah.beta.core.serialization.BigDecimalJsonSerializer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable

@Serializable
data class TripSummaryPublicPrice(
    val version: String, val currency: String, val components: List<Component>,
    @Serializable(with = BigDecimalJsonSerializer::class) val servicesSubtotalUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val supportRate: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val supportAmountUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val paymentRate: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val paymentAmountUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val roundingUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val totalUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val perPilgrimUsd: BigDecimal,
) {
    @Serializable data class Component(val code: String, val label: String,
        @Serializable(with = BigDecimalJsonSerializer::class) val amountUsd: BigDecimal,
        val cancellationPolicy: String? = null)

    fun reconciles(total: BigDecimal): Boolean {
        fun cents(n: BigDecimal) = n.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (version != "trip-summary-v2" || currency != "USD" ||
            supportRate.compareTo(BigDecimal("0.09")) != 0 || paymentRate.compareTo(BigDecimal("0.02")) != 0 ||
            components.isEmpty() || components.map { it.code }.distinct().size != components.size ||
            components.any { it.amountUsd.signum() < 0 }) return false
        return cents(totalUsd) == cents(total) &&
            components.fold(BigDecimal.ZERO) { sum, row -> sum + cents(row.amountUsd) }.compareTo(cents(servicesSubtotalUsd)) == 0 &&
            cents(servicesSubtotalUsd) + cents(supportAmountUsd) + cents(paymentAmountUsd) + cents(roundingUsd) == cents(totalUsd) &&
            cents(servicesSubtotalUsd * supportRate) == cents(supportAmountUsd) && cents(totalUsd * paymentRate) == cents(paymentAmountUsd)
    }
}

@Serializable data class TripSummaryRoomPolicy(val minimumRooms: Int, val requiresAdultException: Boolean)
@Serializable data class PackageItineraryPreviewItem(val id: String, val dateLocal: String, val title: String,
    val subtitle: String, val icon: String, val location: String, val timeLocal: String? = null,
    val endTimeLocal: String? = null, val kind: String? = null)
