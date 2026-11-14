package com.iumrah.beta.data.booking

import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.domain.booking.BookingDraftBuilder
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

data class BookingStoreState(
    val sessions: List<StoredBookingSession> = emptyList(),
    val esimProfilesByBooking: Map<String, List<ClientESIMProfile>> = emptyMap(),
    val isMutating: Boolean = false,
    val lastError: String? = null,
    val pushRegistrationReady: Boolean? = null,
    val pushRegistrationError: String? = null,
)

class BookingStore(
    val service: BookingService,
    private val accountStore: IumrahAccountStore,
    private val vault: SecureJsonStore,
    private val packageEngine: RemotePackageEngineClient,
) {
    private val serializer = ListSerializer(StoredBookingSession.serializer())
    private val _state = MutableStateFlow(BookingStoreState(load()))
    val state: StateFlow<BookingStoreState> = _state.asStateFlow()

    fun booking(id: String): StoredBookingSession? = _state.value.sessions.firstOrNull { it.id == id }

    /** Read-only account proof for fallback, not persisted with individual bookings. */
    fun accountBearerToken(): String? = accountStore.bearerToken

    fun esimProfiles(bookingID: String): List<ClientESIMProfile> = _state.value.esimProfilesByBooking[bookingID].orEmpty()
    fun primaryESIM(bookingID: String): ClientESIMProfile? = esimProfiles(bookingID).firstOrNull()

    suspend fun loadESIMs(bookingID: String): List<ClientESIMProfile> {
        val session = booking(bookingID) ?: run {
            _state.update { it.copy(esimProfilesByBooking = it.esimProfilesByBooking - bookingID) }
            return emptyList()
        }
        val headers = headersFor(session)
        if (headers.isEmpty()) {
            _state.update { it.copy(esimProfilesByBooking = it.esimProfilesByBooking - bookingID) }
            return emptyList()
        }
        val response = service.fetchOperationalTrip(bookingID, headers)
        val profiles = response.esims.orEmpty()
        _state.update { current ->
            current.copy(esimProfilesByBooking = if (profiles.isEmpty()) current.esimProfilesByBooking - bookingID else current.esimProfilesByBooking + (bookingID to profiles))
        }
        return profiles
    }

    fun headersFor(session: StoredBookingSession): Map<String, String> =
        accountStore.authorizationHeaders(session.accessToken).ifEmpty {
            session.accessToken.takeIf { it.isNotBlank() }?.let { mapOf("x-booking-token" to it) }.orEmpty()
        }

    suspend fun create(
        journey: JourneyState,
        quote: PackageQuote,
        language: AppLanguage,
        pilgrimProfile: BookingPilgrimProfile?,
    ): StoredBookingSession {
        _state.update { it.copy(isMutating = true, lastError = null) }
        try {
            val quoteProof = quote.quoteProof?.trim().orEmpty()
            if (quote.bookingAllowed == false || quote.quoteId?.startsWith("server-") != true || quoteProof.isEmpty()) {
                throw IllegalStateException("The package price must be confirmed by the secure iumrah PackageEngine before booking.")
            }
            val payload = BookingDraftBuilder.make(journey, quote, language, pilgrimProfile)
            val response = service.createBooking(payload)
            val token = response.accessToken?.trim().orEmpty()
            if (token.isBlank()) throw APIException.MissingBookingToken
            val serverProfile = response.booking.pilgrimProfile ?: pilgrimProfile
            var session = StoredBookingSession(
                id = response.booking.id,
                accessToken = token,
                booking = response.booking,
                travelerName = serverProfile?.displayName,
                telegram = serverProfile?.telegram,
                whatsapp = serverProfile?.whatsapp,
                hotelSelection = journey.makkahHotel?.let { BookingHotelSelectionSnapshot.from(it, journey.makkahRoom, journey.makkahRoomCategory) },
                madinahHotelSelection = journey.madinahHotel?.let { BookingHotelSelectionSnapshot.from(it, journey.madinahRoom, journey.madinahRoomCategory) },
                transferVehicle = journey.resolvedTransferVehicle,
            )

            session = session.copy(pendingGeneratorQuoteProof = quoteProof)
            upsert(session)
            if (commitPricingReportWithRetry(session.id, session.accessToken, quoteProof)) {
                session = session.copy(pendingGeneratorQuoteProof = null)
                upsert(session)
            }

            accountStore.bearerToken?.takeIf { it.isNotBlank() }?.let {
                runCatching { accountStore.linkBooking(session.id, session.accessToken) }.getOrNull()?.let { linked ->
                    session = session.copy(
                        pilgrimID = linked.pilgrimID,
                        bookingNumber = linked.bookingNumber,
                        bookingDisplayNumber = linked.bookingDisplayNumber,
                    )
                }
            }

            val operational = syncGeneratorReportWithRetry(
                id = session.id,
                accessToken = session.accessToken,
                trace = payload.booking.generatorTrace,
            )
            if (operational != null) {
                session = session.mergeOperationalTrip(
                    trip = operational.trip,
                    history = operational.statusHistory,
                    assignment = operational.assignment,
                )
            }

            if (serverProfile != null && serverProfile.firstName.isNotBlank() && serverProfile.lastName.isNotBlank()) {
                runCatching {
                    service.syncBookingProfile(
                        session.id,
                        session.accessToken,
                        serverProfile,
                        if (operational == null) payload.booking.generatorTrace else null,
                    )
                }.getOrNull()?.let { synced ->
                    session = session.mergeOperationalTrip(
                        trip = synced.trip,
                        history = synced.statusHistory,
                        assignment = synced.assignment,
                    )
                }
            }

            if (session.pendingGeneratorQuoteProof != null && commitPricingReportWithRetry(session.id, session.accessToken, quoteProof)) {
                session = session.copy(pendingGeneratorQuoteProof = null)
            }
            upsert(session)
            _state.update { it.copy(isMutating = false) }
            return session
        } catch (error: Throwable) {
            _state.update { it.copy(isMutating = false, lastError = error.message) }
            throw error
        }
    }

    private suspend fun commitPricingReportWithRetry(id: String, accessToken: String, quoteProof: String): Boolean {
        repeat(3) { attempt ->
            val ok = runCatching { packageEngine.commitPricingReport(id, accessToken, quoteProof); true }.getOrDefault(false)
            if (ok) return true
            if (attempt < 2) delay(350L * (attempt + 1))
        }
        return false
    }

    suspend fun restoreAccountTrips() {
        if (accountStore.bearerToken.isNullOrBlank()) return
        val trips = runCatching { accountStore.accountTrips() }.getOrDefault(emptyList())
        for (trip in trips) {
            val detail = runCatching { accountStore.tripDetail(trip.bookingID) }.getOrNull() ?: continue
            val remote = detail.booking
            val existing = booking(trip.bookingID)
            var session = if (existing != null) {
                existing.copy(
                    booking = remote,
                    travelerName = remote.pilgrimProfile?.displayName ?: existing.travelerName,
                    telegram = remote.pilgrimProfile?.telegram ?: existing.telegram,
                    whatsapp = remote.pilgrimProfile?.whatsapp ?: existing.whatsapp,
                    hotelSelection = remote.hotelSelection ?: existing.hotelSelection,
                    madinahHotelSelection = remote.madinahHotelSelection ?: existing.madinahHotelSelection,
                )
            } else {
                StoredBookingSession(
                    id = trip.bookingID,
                    accessToken = "",
                    booking = remote,
                    travelerName = remote.pilgrimProfile?.displayName,
                    telegram = remote.pilgrimProfile?.telegram,
                    whatsapp = remote.pilgrimProfile?.whatsapp,
                    hotelSelection = remote.hotelSelection,
                    madinahHotelSelection = remote.madinahHotelSelection,
                )
            }
            session = session.mergeOperationalTrip(detail.trip, detail.statusHistory, detail.assignment)
            detail.esims?.let { profiles ->
                _state.update { current ->
                    current.copy(esimProfilesByBooking = if (profiles.isEmpty()) current.esimProfilesByBooking - trip.bookingID else current.esimProfilesByBooking + (trip.bookingID to profiles))
                }
            }
            upsert(session)
        }
    }

    suspend fun refreshAll() {
        _state.value.sessions.map { it.id }.forEach { id -> runCatching { refresh(id) } }
    }

    suspend fun syncPushSubscriptions(deviceToken: String, locale: String) {
        val token = deviceToken.trim()
        if (token.isEmpty()) return

        var registeredAny = false
        var observedReady: Boolean? = null
        var firstError: String? = null

        for (session in _state.value.sessions) {
            val candidates = buildList {
                accountStore.bearerToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(mapOf("Authorization" to "Bearer $it"))
                }
                session.accessToken.trim().takeIf { it.isNotEmpty() }?.let {
                    add(mapOf("x-booking-token" to it))
                }
            }.distinct()
            if (candidates.isEmpty()) continue

            var registered = false
            var lastError: Throwable? = null
            for (headers in candidates) {
                try {
                    val response = service.registerPushDevice(session.id, headers, token, locale)
                    registeredAny = true
                    registered = true
                    response.ready?.let { ready -> observedReady = (observedReady ?: true) && ready }
                    break
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            if (!registered && firstError == null) firstError = lastError?.message
        }

        _state.update { current ->
            current.copy(
                pushRegistrationReady = if (registeredAny) observedReady else current.pushRegistrationReady,
                pushRegistrationError = firstError,
            )
        }
    }

    suspend fun refresh(id: String): StoredBookingSession? {
        val current = booking(id) ?: return null
        // Account-linked trips can have no booking token. Use the account's own trip endpoint
        // instead of making a request with an empty x-booking-token proof.
        val accountDetail = if (current.accessToken.isBlank() && !accountStore.bearerToken.isNullOrBlank()) {
            runCatching { accountStore.tripDetail(id) }.getOrNull()
        } else null
        val remote = accountDetail?.booking ?: current.accessToken.takeIf { it.isNotBlank() }?.let {
            runCatching { service.fetchBooking(id, it) }.getOrNull()
        }
        val operational = if (accountDetail != null) null else
            runCatching { service.fetchOperationalTrip(id, headersFor(current)) }.getOrNull()
        var next = current
        if (remote != null) {
            next = next.copy(
                booking = remote,
                travelerName = remote.pilgrimProfile?.displayName ?: next.travelerName,
                telegram = remote.pilgrimProfile?.telegram ?: next.telegram,
                whatsapp = remote.pilgrimProfile?.whatsapp ?: next.whatsapp,
                hotelSelection = remote.hotelSelection ?: next.hotelSelection,
                madinahHotelSelection = remote.madinahHotelSelection ?: next.madinahHotelSelection,
            )
        }
        if (accountDetail != null) {
            next = next.mergeOperationalTrip(accountDetail.trip, accountDetail.statusHistory, accountDetail.assignment)
            accountDetail.esims?.let { profiles ->
                _state.update { state -> state.copy(esimProfilesByBooking = if (profiles.isEmpty()) state.esimProfilesByBooking - id else state.esimProfilesByBooking + (id to profiles)) }
            }
        }
        if (operational != null) {
            operational.esims?.let { profiles ->
                _state.update { current ->
                    current.copy(esimProfilesByBooking = if (profiles.isEmpty()) current.esimProfilesByBooking - id else current.esimProfilesByBooking + (id to profiles))
                }
            }
            next = next.mergeOperationalTrip(
                trip = operational.trip,
                history = operational.statusHistory,
                assignment = operational.assignment,
            )
        }
        val pendingProof = next.pendingGeneratorQuoteProof
        if (!pendingProof.isNullOrBlank() && next.accessToken.isNotBlank() && commitPricingReportWithRetry(id, next.accessToken, pendingProof)) {
            next = next.copy(pendingGeneratorQuoteProof = null)
        }
        upsert(next)
        return next
    }

    suspend fun updateHotel(
        bookingID: String,
        role: String,
        hotel: HotelSummary,
        room: HotelRoom?,
        category: IumrahRoomCategoryOption?,
    ) {
        val session = booking(bookingID) ?: throw APIException.MissingBookingToken
        val headers = headersFor(session)
        if (headers.isEmpty()) throw APIException.MissingBookingToken
        service.updateHotelSelection(bookingID, headers, role, hotel, room, category)
        val snapshot = BookingHotelSelectionSnapshot.from(hotel, room, category)
        val next = if (role == "madinah") session.copy(madinahHotelSelection = snapshot, pendingChangeConfirmation = true)
        else session.copy(hotelSelection = snapshot, pendingChangeConfirmation = true)
        upsert(next)
    }

    suspend fun updateContacts(bookingID: String, telegram: String, whatsapp: String) {
        val session = booking(bookingID) ?: throw APIException.MissingBookingToken
        val headers = headersFor(session)
        service.updateContacts(bookingID, headers, telegram.trim(), whatsapp.trim())
        upsert(session.copy(telegram = telegram.trim(), whatsapp = whatsapp.trim(), pendingChangeConfirmation = true))
    }

    suspend fun updateZiyarat(bookingID: String, makkah: Boolean, madinah: Boolean) {
        val session = booking(bookingID) ?: throw APIException.MissingBookingToken
        service.updateZiyarat(bookingID, headersFor(session), makkah, madinah)
        upsert(session.copy(ziyaratMakkahOverride = makkah, ziyaratMadinahOverride = madinah, pendingChangeConfirmation = true))
    }

    suspend fun updateESIM(bookingID: String, enabled: Boolean) {
        val session = booking(bookingID) ?: throw APIException.MissingBookingToken
        service.updateESIM(bookingID, headersFor(session), enabled)
        upsert(session.copy(esimOverride = enabled, pendingChangeConfirmation = true))
    }

    fun clearPendingConfirmation(bookingID: String) {
        booking(bookingID)?.let { upsert(it.copy(pendingChangeConfirmation = false)) }
    }

    suspend fun deleteBooking(bookingID: String) {
        val session = booking(bookingID) ?: return purge(bookingID)
        service.deleteBooking(bookingID, headersFor(session))
        purge(bookingID)
    }

    private suspend fun syncGeneratorReportWithRetry(
        id: String,
        accessToken: String,
        trace: BookingGeneratorTrace?,
    ): ClientTripResponse? {
        val waits = listOf(0L, 350L, 800L)
        for (wait in waits) {
            if (wait > 0) delay(wait)
            runCatching { service.syncGeneratorReport(id, accessToken, trace) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun upsert(session: StoredBookingSession) {
        val values = _state.value.sessions.toMutableList()
        val index = values.indexOfFirst { it.id == session.id }
        if (index >= 0) values[index] = session else values.add(0, session)
        _state.update { it.copy(sessions = values) }
        persist(values)
    }

    private fun purge(id: String) {
        val values = _state.value.sessions.filterNot { it.id == id }
        _state.update { it.copy(sessions = values) }
        persist(values)
    }

    private fun persist(values: List<StoredBookingSession>) = vault.write(VAULT_KEY, values, serializer)
    private fun load(): List<StoredBookingSession> = vault.read(VAULT_KEY, serializer).orEmpty()

    companion object { private const val VAULT_KEY = "iumrah-booking-sessions-v2" }
}
