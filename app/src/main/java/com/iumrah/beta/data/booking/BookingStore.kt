package com.iumrah.beta.data.booking

import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.domain.booking.BookingDraftBuilder
import com.iumrah.beta.domain.booking.BookingAuthFallbackPolicy
import com.iumrah.beta.domain.booking.BookingSessionPolicy
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val createMutex = Mutex()
    private val refreshMutex = Mutex()
    private val restoreMutex = Mutex()
    private val linkMutex = Mutex()
    private var linkedAccountToken: String? = null
    private val linkedBookings = mutableSetOf<Pair<String, String>>()
    private val serializer = ListSerializer(StoredBookingSession.serializer())
    private val _state = MutableStateFlow(BookingStoreState(load().filter(::isVisible)))
    val state: StateFlow<BookingStoreState> = _state.asStateFlow()

    private fun isVisible(session: StoredBookingSession): Boolean = BookingSessionPolicy.visible(
        session.accessToken, session.accountOwnerID, accountStore.state.value.iumrahID,
    )

    fun booking(id: String): StoredBookingSession? = _state.value.sessions.firstOrNull { it.id == id && isVisible(it) }

    /** Keep guest proofs, but remove previous-account-only cache entries from this device. */
    fun reconcileAccountScope() {
        val current = _state.value
        val visible = current.sessions.filter(::isVisible)
        if (visible != current.sessions) {
            val ids = visible.map { it.id }.toSet()
            _state.update { it.copy(sessions = visible, esimProfilesByBooking = it.esimProfilesByBooking.filterKeys { id -> id in ids }) }
            persist(visible)
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) { null }

    private fun mergeHotel(remote: BookingHotelSelectionSnapshot?, local: BookingHotelSelectionSnapshot?): BookingHotelSelectionSnapshot? {
        if (remote == null) return local
        return if (BookingSessionPolicy.preserveRoom(local?.hotelId, local?.roomCategory != null, remote.hotelId, remote.roomCategory != null, remote.roomId)) local else remote
    }

    private fun mergeRemote(session: StoredBookingSession, remote: RemoteBooking): StoredBookingSession = session.copy(
        booking = remote,
        travelerName = remote.pilgrimProfile?.displayName ?: session.travelerName,
        telegram = remote.pilgrimProfile?.telegram ?: session.telegram,
        whatsapp = remote.pilgrimProfile?.whatsapp ?: session.whatsapp,
        hotelSelection = mergeHotel(remote.hotelSelection, session.hotelSelection),
        madinahHotelSelection = mergeHotel(remote.madinahHotelSelection, session.madinahHotelSelection),
        ziyaratMakkahOverride = remote.customization?.ziyaratMakkah ?: session.ziyaratMakkahOverride,
        ziyaratMadinahOverride = remote.customization?.ziyaratMadinah ?: session.ziyaratMadinahOverride,
        esimOverride = remote.customization?.esim ?: session.esimOverride,
    )

    private fun updateESIMCache(id: String, profiles: List<ClientESIMProfile>?) {
        if (profiles == null) return
        _state.update { it.copy(esimProfilesByBooking = if (profiles.isEmpty()) it.esimProfilesByBooking - id else it.esimProfilesByBooking + (id to profiles)) }
    }

    /** Same per-account/per-booking memo as iOS; failed links remain retryable. */
    suspend fun linkLocalBookingsToAccount(onlyBookingID: String? = null) = linkMutex.withLock {
        val token = accountStore.bearerToken
        if (linkedAccountToken != token) {
            linkedAccountToken = token
            linkedBookings.clear()
        }
        if (token.isNullOrBlank()) return@withLock
        val sessions = _state.value.sessions.filter { it.accessToken.isNotBlank() }
        linkedBookings.retainAll(sessions.map { it.id to it.accessToken }.toSet())
        for (session in sessions) {
            if (onlyBookingID != null && session.id != onlyBookingID) continue
            if (accountStore.bearerToken != token) return@withLock
            val key = session.id to session.accessToken
            if (key in linkedBookings) continue
            val linked = attempt { accountStore.linkBookingForAccount(session.id, session.accessToken, token) } ?: continue
            if (accountStore.bearerToken != token) return@withLock
            val latest = booking(session.id) ?: continue
            if (latest.accessToken != session.accessToken || !linked.ok) continue
            upsert(latest.copy(pilgrimID = linked.pilgrimID, bookingNumber = linked.bookingNumber ?: latest.bookingNumber,
                bookingDisplayNumber = linked.bookingDisplayNumber ?: latest.bookingDisplayNumber))
            linkedBookings.add(key)
        }
    }

    /** Read-only account proof for fallback, not persisted with individual bookings. */
    fun accountBearerToken(): String? = accountStore.bearerToken

    fun canUseCareChat(): Boolean = !accountBearerToken().isNullOrBlank() &&
        state.value.sessions.any { isVisible(it) && it.effectiveStatus.uppercase() !in setOf("COMPLETED", "CANCELLED") }

    fun careHeaders(bookingID: String): Map<String, String> {
        if (bookingID == "account") {
            if (!canUseCareChat()) return emptyMap()
            return accountBearerToken()?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        }
        return booking(bookingID)?.let(::headersFor).orEmpty()
    }

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
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // Do not let an old account/session response repopulate private activation credentials.
        val currentSession = booking(bookingID) ?: return emptyList()
        if (headersFor(currentSession) != headers) return emptyList()
        val profiles = response.esims.orEmpty()
        _state.update { current ->
            if (current.sessions.none { it.id == bookingID && isVisible(it) && headersFor(it) == headers }) current
            else current.copy(esimProfilesByBooking = if (profiles.isEmpty()) current.esimProfilesByBooking - bookingID else current.esimProfilesByBooking + (bookingID to profiles))
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
        // Reject concurrent checkout submissions rather than queueing a second booking.
        check(createMutex.tryLock()) { "Booking creation is already in progress." }
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
                session = (booking(session.id) ?: session).copy(pendingGeneratorQuoteProof = null)
                upsert(session)
            }

            linkLocalBookingsToAccount(onlyBookingID = session.id)
            session = booking(session.id) ?: session

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
        } finally {
            createMutex.unlock()
        }
    }

    private suspend fun commitPricingReportWithRetry(id: String, accessToken: String, quoteProof: String): Boolean {
        repeat(3) { attempt ->
            val ok = attempt { packageEngine.commitPricingReport(id, accessToken, quoteProof); true } ?: false
            if (ok) return true
            if (attempt < 2) delay(350L * (attempt + 1))
        }
        return false
    }

    suspend fun restoreAccountTrips() = restoreMutex.withLock {
        reconcileAccountScope()
        val token = accountStore.bearerToken ?: return@withLock
        val owner = accountStore.state.value.iumrahID ?: return@withLock
        val trips = attempt { accountStore.accountTrips(token) } ?: return@withLock
        for (trip in trips) {
            if (accountStore.bearerToken != token) return@withLock
            val detail = attempt { accountStore.tripDetail(trip.bookingID, token) } ?: continue
            if (accountStore.bearerToken != token) return@withLock
            if (detail.booking.id != trip.bookingID || detail.trip.bookingID != trip.bookingID) continue
            val existing = booking(trip.bookingID)
            val initial = existing ?: StoredBookingSession(id = trip.bookingID, accessToken = "", booking = detail.booking, accountOwnerID = owner)
            val merged = mergeRemote(initial, detail.booking).mergeOperationalTrip(detail.trip, detail.statusHistory, detail.assignment)
            upsert(merged)
            updateESIMCache(trip.bookingID, detail.esims)
        }
    }

    suspend fun refreshAll() {
        if (!refreshMutex.tryLock()) {
            refreshMutex.withLock { } // await the in-flight refresh, no duplicate fetch
            return
        }
        try {
            reconcileAccountScope()
            _state.value.sessions.map { it.id }.forEach { id -> attempt { refresh(id) } }
        } finally { refreshMutex.unlock() }
    }

    suspend fun syncPushSubscriptions(deviceToken: String, locale: String) {
        val token = deviceToken.trim()
        if (token.isEmpty()) return

        var registeredAny = false
        var observedReady: Boolean? = null
        var firstError: String? = null

        for (session in _state.value.sessions.filter(::isVisible)) {
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
                } catch (error: CancellationException) {
                    throw error
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

    suspend fun refreshCreatedBookingStatus(id: String): StoredBookingSession? {
        val initial = booking(id) ?: return null
        val token = initial.accessToken.trim().takeIf { it.isNotEmpty() } ?: return initial
        val response = attempt { service.fetchOperationalTrip(id, mapOf("x-booking-token" to token)) } ?: return booking(id)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val current = booking(id) ?: return null
        if (response.trip.bookingID != id || current.accessToken != initial.accessToken || current.operationStatus != initial.operationStatus || current.effectiveStatus != initial.effectiveStatus) return current
        val next = current.mergeOperationalTrip(response.trip, response.statusHistory, response.assignment)
        if (next != current) upsert(next)
        return booking(id)
    }

    suspend fun refresh(id: String): StoredBookingSession? {
        val current = booking(id) ?: return null
        val accountToken = accountStore.bearerToken
        val bookingToken = current.accessToken.trim()
        var accountDetail: com.iumrah.beta.models.account.IumrahAccountTripDetailResponse? = null
        var remote: RemoteBooking? = null
        var operational: ClientTripResponse? = null
        if (bookingToken.isNotEmpty()) {
            try {
                remote = service.fetchBooking(id, bookingToken)
            } catch (error: APIException) {
                if (BookingAuthFallbackPolicy.isAuthorizationFailure(error) && !accountToken.isNullOrBlank() && accountStore.bearerToken == accountToken) {
                    accountDetail = attempt { accountStore.tripDetail(id, accountToken) }
                } else if (BookingAuthFallbackPolicy.isConfirmedMissing(error)) {
                    if (booking(id)?.accessToken == current.accessToken) purge(id)
                    return null
                } else throw error
            }
            if (remote != null) {
                val profile = remote.pilgrimProfile
                if (profile != null && profile.firstName.isNotBlank() && profile.lastName.isNotBlank()) {
                    operational = attempt { service.syncBookingProfile(id, bookingToken, profile) }
                }
                if (operational == null) operational = attempt { service.fetchOperationalTrip(id, mapOf("x-booking-token" to bookingToken)) }
            }
        } else if (!accountToken.isNullOrBlank()) {
            accountDetail = try { accountStore.tripDetail(id, accountToken) } catch (error: APIException) {
                if (BookingAuthFallbackPolicy.isConfirmedMissing(error) && accountStore.bearerToken == accountToken) { purge(id); return null }
                throw error
            }
        }
        if (accountDetail != null && accountStore.bearerToken != accountToken) return booking(id)
        if (accountDetail != null && (accountDetail.booking.id != id || accountDetail.trip.bookingID != id)) return booking(id)
        if (remote != null && remote.id != id) return booking(id)
        if (operational != null && operational.trip.bookingID != id) return booking(id)
        var next = booking(id) ?: return null // never resurrect a locally deleted trip
        if (next.accessToken != current.accessToken) return next
        val fetched = accountDetail?.booking ?: remote
        if (fetched != null) next = mergeRemote(next, fetched)
        if (accountDetail != null) {
            next = next.mergeOperationalTrip(accountDetail.trip, accountDetail.statusHistory, accountDetail.assignment)
            updateESIMCache(id, accountDetail.esims)
        } else if (operational != null) {
            next = next.mergeOperationalTrip(operational.trip, operational.statusHistory, operational.assignment)
            updateESIMCache(id, operational.esims)
        }
        val pendingProof = next.pendingGeneratorQuoteProof
        upsert(next)
        if (!pendingProof.isNullOrBlank() && bookingToken.isNotEmpty() && commitPricingReportWithRetry(id, bookingToken, pendingProof)) {
            val latest = booking(id) ?: return null
            if (latest.pendingGeneratorQuoteProof == pendingProof && latest.accessToken == current.accessToken) {
                next = latest.copy(pendingGeneratorQuoteProof = null)
                upsert(next)
            }
        }
        return booking(id)
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
            attempt { service.syncGeneratorReport(id, accessToken, trace) }?.let { return it }
        }
        return null
    }

    private fun upsert(session: StoredBookingSession) {
        val values = _state.value.sessions.toMutableList()
        val index = values.indexOfFirst { it.id == session.id }
        if (index >= 0) values[index] = session else values.add(0, session)
        if (!isVisible(session)) return
        val sorted = values.sortedByDescending { it.booking.createdAt }
        _state.update { it.copy(sessions = sorted) }
        persist(sorted)
    }

    private fun purge(id: String) {
        val values = _state.value.sessions.filterNot { it.id == id }
        _state.update { it.copy(sessions = values, esimProfilesByBooking = it.esimProfilesByBooking - id) }
        persist(values)
    }

    private fun persist(values: List<StoredBookingSession>) = vault.write(VAULT_KEY, values, serializer)
    private fun load(): List<StoredBookingSession> = vault.read(VAULT_KEY, serializer).orEmpty()

    companion object { private const val VAULT_KEY = "iumrah-booking-sessions-v2" }
}
