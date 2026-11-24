package com.iumrah.beta.data.hotel

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.models.hotel.HotelDetail
import com.iumrah.beta.models.hotel.HotelDetailResponse
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.HotelsResponse
import com.iumrah.beta.models.hotel.StorefrontFlightBoardResponse
import com.iumrah.beta.models.hotel.StorefrontPackageEnvelope
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.models.hotel.StorefrontPackageRefreshEnvelope
import com.iumrah.beta.models.hotel.StorefrontPackagesEnvelope
import com.iumrah.beta.models.hotel.StorefrontFlightLeg
import kotlinx.serialization.Serializable

class HotelCatalogService(private val api: APIClient) {
    suspend fun listHotels(city: String): List<HotelSummary> =
        api.get<HotelsResponse>("/api/catalog/hotels", query = mapOf("city" to city)).hotels

    /** iOS parity: the Business catalogue has historically used several English
     * spellings for Makkah/Madinah. The iOS storefront queries all aliases and
     * deduplicates by hotel id so a backend spelling never makes Android look empty. */
    suspend fun listHotels(cities: List<String>): List<HotelSummary> {
        val merged = linkedMapOf<String, HotelSummary>()
        var lastError: Throwable? = null
        for (city in cities) {
            try { listHotels(city).forEach { merged[it.id] = it } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { lastError = error }
        }
        if (merged.isEmpty() && lastError != null) throw lastError as Throwable
        return merged.values.sortedWith(
            compareByDescending<HotelSummary> { it.stars ?: 0 }.thenBy { it.name.lowercase() },
        )
    }

    suspend fun hotelDetail(id: String): HotelDetail =
        api.get<HotelDetailResponse>("/api/catalog/hotels/$id").let { response ->
            check(response.ok && response.hotel.id == id) { "Hotel response mismatch" }; response.hotel
        }

    suspend fun storefrontFlightBoard(origin: String): StorefrontFlightBoardResponse =
        api.get(
            "/api/package/storefront/flights",
            query = mapOf("origin" to origin.trim().uppercase()),
            timeoutSeconds = 10,
        )

    suspend fun storefrontPackages(mode: String, origin: String, limit: Int): StorefrontPackagesEnvelope {
        val response: StorefrontPackagesEnvelope = api.get(
            "/api/storefront/packages",
            query = mapOf(
                "mode" to mode,
                "origins" to origin.trim().uppercase(),
                "limit" to limit.toString(),
            ),
            timeoutSeconds = 25,
        )
        if (!response.ok) throw APIException.InvalidResponse
        return response
    }

    suspend fun refreshStorefrontPackages(mode: String, origin: String, cursor: Int): StorefrontPackageRefreshEnvelope {
        val response: StorefrontPackageRefreshEnvelope = api.post(
            "/api/storefront/packages",
            StorefrontPackageRefreshRequest(
                mode = mode,
                origin = origin.trim().uppercase(),
                cursor = maxOf(0, cursor),
            ),
            timeoutSeconds = 45,
        )
        if (!response.ok) throw APIException.InvalidResponse
        return response
    }

    suspend fun storefrontPackage(id: String): StorefrontPackageSnapshot =
        api.get<StorefrontPackageEnvelope>(
            "/api/storefront/packages/$id",
            timeoutSeconds = 15,
        ).let { response ->
            check(response.ok && response.`package`.id == id) { "Package response mismatch" }
            response.`package`
        }

    suspend fun storefrontPackageQuote(
        snapshot: StorefrontPackageSnapshot,
        outbound: StorefrontFlightLeg,
        inbound: StorefrontFlightLeg,
        outboundOfferId: String,
        inboundOfferId: String,
        adults: Int,
        children: Int,
        infants: Int,
        rooms: Int,
        makkahLunch: Boolean,
        makkahDinner: Boolean,
        madinahDinner: Boolean,
        transferVehicle: String?,
        haramainEnabled: Boolean,
        haramainFareClass: String,
        haramainTicketCount: Int,
        makkahHotelIdOverride: String? = null,
        madinahHotelIdOverride: String? = null,
        makkahNightsOverride: Int? = null,
        madinahNightsOverride: Int? = null,
        includeMadinahOverride: Boolean? = null,
        makkahRoomIdOverride: String? = snapshot.configuration?.makkahRoomId,
        madinahRoomIdOverride: String? = snapshot.configuration?.madinahRoomId,
    ): PackageQuote {
        val makkahHotelId = makkahHotelIdOverride ?: requireNotNull(snapshot.makkahHotelId ?: snapshot.hotelFirstAnchorHotelId) { "Makkah hotel is missing from package snapshot." }
        val includeMadinah = includeMadinahOverride ?: ((snapshot.madinahNights ?: 0) > 0 && !snapshot.madinahHotelId.isNullOrBlank())
        val resolvedMadinahHotelId = if (includeMadinah) {
            madinahHotelIdOverride ?: requireNotNull(snapshot.madinahHotelId) { "Madinah hotel is missing from package configuration." }
        } else null
        val provider = snapshot.providerItineraryId?.takeIf { it.isNotBlank() && outboundOfferId == snapshot.outboundOfferId && inboundOfferId == snapshot.inboundOfferId }
            ?: if (outboundOfferId == inboundOfferId) "curated:$outboundOfferId" else "curated:$outboundOfferId+$inboundOfferId"
        val onLap = minOf(maxOf(0, infants), maxOf(1, adults))
        val request = StorefrontQuoteRequest(
            tier = snapshot.tier ?: "standard",
            tripType = "roundTrip",
            includeMadinah = includeMadinah,
            travelers = StorefrontQuoteRequest.Travelers(adults, children, infants, rooms),
            meals = StorefrontQuoteRequest.Meals(makkahLunch, makkahDinner, madinahDinner),
            transferVehicle = transferVehicle,
            haramain = StorefrontQuoteRequest.Haramain(haramainEnabled, haramainFareClass, maxOf(0, haramainTicketCount)),
            flight = StorefrontQuoteRequest.Flight(
                providerItineraryId = provider,
                cabinClass = outbound.cabinClass.ifBlank { "economy" },
                infantsInSeat = maxOf(0, infants - onLap),
                infantsOnLap = onLap,
                legs = listOf(
                    StorefrontQuoteRequest.Flight.Leg(outbound.origin, outbound.destination, outbound.departureAt.take(10)),
                    StorefrontQuoteRequest.Flight.Leg(inbound.origin, inbound.destination, inbound.departureAt.take(10)),
                ),
            ),
            hotels = StorefrontQuoteRequest.Hotels(
                makkah = StorefrontQuoteRequest.Hotel(
                    makkahHotelId,
                    makkahRoomIdOverride,
                    maxOf(1, makkahNightsOverride ?: snapshot.makkahNights ?: 1),
                ),
                madinah = if (includeMadinah) StorefrontQuoteRequest.Hotel(
                    hotelId = requireNotNull(resolvedMadinahHotelId),
                    roomId = madinahRoomIdOverride,
                    nights = maxOf(1, madinahNightsOverride ?: snapshot.madinahNights ?: 1),
                ) else null,
            ),
        )
        val response = api.post<StorefrontQuoteEnvelope, StorefrontQuoteRequest>(
            "/api/package/quote",
            request,
            timeoutSeconds = 180,
        )
        if (!response.ok) throw APIException.InvalidResponse
        val quote = response.quote
        if (quote.totalPackagePrice.signum() <= 0 || quote.pricePerPerson.signum() <= 0 ||
            quote.quoteId?.startsWith("server-") != true || quote.quoteProof.isNullOrBlank() || quote.currency != "USD")
            throw APIException.Server(502, "PACKAGE_QUOTE_INVALID")
        if (quote.tripSummary?.reconciles(quote.totalPackagePrice) == false)
            throw APIException.Server(502, "PACKAGE_PRICE_BREAKDOWN_INVALID")
        return quote
    }
}


@Serializable
private data class StorefrontQuoteEnvelope(val ok: Boolean, val quote: PackageQuote)

@Serializable
private data class StorefrontQuoteRequest(
    val tier: String,
    val tripType: String,
    val includeMadinah: Boolean,
    val travelers: Travelers,
    val meals: Meals,
    val transferVehicle: String?,
    val haramain: Haramain,
    val flight: Flight,
    val hotels: Hotels,
) {
    @Serializable data class Travelers(val adults: Int, val children: Int, val infants: Int, val rooms: Int)
    @Serializable data class Meals(val makkahLunch: Boolean, val makkahDinner: Boolean, val madinahDinner: Boolean)
    @Serializable data class Haramain(val enabled: Boolean, val fareClass: String, val ticketCount: Int)
    @Serializable data class Flight(
        val providerItineraryId: String,
        val cabinClass: String,
        val infantsInSeat: Int,
        val infantsOnLap: Int,
        val legs: List<Leg>,
    ) { @Serializable data class Leg(val origin: String, val destination: String, val departureDate: String) }
    @Serializable data class Hotel(val hotelId: String, val roomId: String?, val nights: Int)
    @Serializable data class Hotels(val makkah: Hotel, val madinah: Hotel?)
}

@Serializable
private data class StorefrontPackageRefreshRequest(
    val mode: String,
    val origin: String,
    val cursor: Int,
)
