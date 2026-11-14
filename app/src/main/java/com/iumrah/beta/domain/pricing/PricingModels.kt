package com.iumrah.beta.domain.pricing

import com.iumrah.beta.core.serialization.BigDecimalJsonSerializer
import com.iumrah.beta.core.serialization.InstantIsoSerializer
import com.iumrah.beta.core.serialization.LocalDateIsoSerializer
import com.iumrah.beta.domain.trip.FlightFareScope
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class BookingTravelers(
    val adults: Int,
    val children: Int,
    val infants: Int,
    val rooms: Int,
)

@Serializable
data class FlightOffer(
    val id: String,
    @Serializable(with = InstantIsoSerializer::class) val departureAt: Instant,
    @Serializable(with = BigDecimalJsonSerializer::class) val totalPackagePrice: BigDecimal,
    val currency: String,
    val sourceLabel: String,
    @Serializable(with = BigDecimalJsonSerializer::class) val fareAmount: BigDecimal? = null,
    val fareScope: FlightFareScope? = null,
    @Serializable(with = InstantIsoSerializer::class) val fareObservedAt: Instant? = null,
    val providerItineraryId: String? = null,
    val sourceCandidateId: String? = null,
)

@Serializable
data class LocalHotelPriceComponent(
    @Serializable(with = BigDecimalJsonSerializer::class) val nightlyUsd: BigDecimal,
    val nights: Int,
    val rooms: Int,
    val hotelId: String,
    val roomId: String? = null,
    val source: String,
) {
    val totalStayUsd: BigDecimal
        get() = nightlyUsd
            .multiply(BigDecimal.valueOf(maxOf(1, rooms).toLong()))
            .multiply(BigDecimal.valueOf(maxOf(1, nights).toLong()))
}

@Serializable
data class GeneratorPricingFare(
    val candidateId: String,
    @Serializable(with = BigDecimalJsonSerializer::class) val amount: BigDecimal,
    val currency: String,
    val fareScope: String,
    val providerId: String,
    val observedAt: String,
    @Serializable(with = LocalDateIsoSerializer::class) val travelDate: LocalDate,
    @Serializable(with = BigDecimalJsonSerializer::class) val normalizedGroupUsd: BigDecimal,
)

@Serializable
data class GeneratorPricingHotelInput(
    @Serializable(with = BigDecimalJsonSerializer::class) val amountUsd: BigDecimal,
    val unit: String,
    val nights: Int,
    val hotelId: String?,
    val roomId: String?,
    val pricingMode: String?,
)

@Serializable
data class GeneratorPricingComponent(
    val code: String,
    val label: String,
    @Serializable(with = BigDecimalJsonSerializer::class) val supplierCostUsd: BigDecimal,
)

@Serializable
data class GeneratorPricingContext(
    val tier: String,
    val tripType: String,
    val includeMadinah: Boolean,
    val totalDays: Int,
    val travelers: BookingTravelers,
    val roomCount: Int,
    val vehicleCount: Int,
)

@Serializable
data class GeneratorPricingInputs(
    val journeyFare: GeneratorPricingFare? = null,
    val outbound: GeneratorPricingFare? = null,
    val inbound: GeneratorPricingFare? = null,
    val makkahHotel: GeneratorPricingHotelInput,
    val madinahHotel: GeneratorPricingHotelInput?,
)

@Serializable
data class GeneratorPricingTotals(
    @Serializable(with = BigDecimalJsonSerializer::class) val supplierCostUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val markupRate: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val markupAmountUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val subtotalAfterMarkupUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val paymentFeeRate: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val paymentFeeAmountUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val calculatedSellingPriceUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val publicPricePerPilgrimUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val publicTotalUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val roundingDifferenceUsd: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val estimatedProfitUsd: BigDecimal,
)

@Serializable
data class GeneratorPricingSnapshot(
    val quoteId: String,
    val pricingVersion: String,
    val currency: String,
    val context: GeneratorPricingContext,
    val selectedPricingInputs: GeneratorPricingInputs,
    val components: List<GeneratorPricingComponent>,
    val totals: GeneratorPricingTotals,
)

@Serializable
data class PackageQuote(
    @Serializable(with = BigDecimalJsonSerializer::class) val totalPackagePrice: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val pricePerPerson: BigDecimal,
    val currency: String,
    val isEstimated: Boolean,
    val quoteId: String? = null,
    val quoteProof: String? = null,
    val pricingSnapshot: GeneratorPricingSnapshot? = null,
    val bookingAllowed: Boolean? = null,
)

internal fun newLocalQuoteId(): String = "local-${UUID.randomUUID().toString().lowercase()}"
