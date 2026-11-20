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

/** Remote account reconciliation must not show a false empty booking list on an API failure. */
enum class AccountTripsRestoreState { NOT_REQUESTED, LOADING, LOADED, FAILED }

data class BookingStoreState(
    val sessions: List<StoredBookingSession> = emptyList(),
    val esimProfilesByBooking: Map<String, List<ClientESIMProfile>> = emptyMap(),
    val isMutating: Boolean = false,
    val accountTripsRestoreState: AccountTripsRestoreState = AccountTripsRestoreState.NOT_REQUESTED,
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
    private var accountRestoreScope: String? = null
    private val linkMutex = Mutex()
    private val pushMutex = Mutex()
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
        // Track the account credential privately. An earlier account's restore
        // lifecycle must never be reused for another identity or for a guest.
        val scopedToken = accountStore.bearerToken
        if (accountRestoreScope != scopedToken) {
            accountRestoreScope = scopedToken
            _state.update { it.copy(accountTripsRestoreState = AccountTripsRestoreState.NOT_REQUESTED) }
        }
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
        val owner = accountStore.state.value.iumrahID
        val sessions = _state.value.sessions.filter { it.accessToken.isNotBlank() && BookingSessionPolicy.sameIdentity(it.pilgrimID, owner) }
        linkedBookings.retainAll(sessions.map { it.id to it.accessToken }.toSet())
        for (session in sessions) {
            if (onlyBookingID != null && session.id != onlyBookingID) continue
            if (accountStore.bearerToken != token) return@withLock
            val key = session.id to session.accessToken
            if (key in linkedBookings) continue
            val linked = attempt { accountStore.linkBookingForAccount(session.id, session.accessToken, token) } ?: continue
            if (accountStore.bearerToken != token) return@withLock
            val latest = booking(session.id) ?: continue
            if (latest.accessToken != session.accessToken || !linked.ok || !BookingSessionPolicy.sameIdentity(linked.pilgrimID, owner)) continue
            upsert(latest.copy(pilgrimID = linked.pilgrimID, bookingNumber = linked.bookingNumber ?: latest.bookingNumber,
                bookingDisplayNumber = linked.bookingDisplayNumber ?: latest.bookingDisplayNumber))
            linkedBookings.add(key)
        }
    }

    /** Read-only account proof for fallback, not persisted with individual bookings. */
    fun accountBearerToken(): String? = accountStore.bearerToken

    private fun careScope(session: StoredBookingSession): String? = BookingSessionPolicy.careScope(
        session.effectiveStatus, session.id, session.accessToken, session.pilgrimID ?: session.accountOwnerID,
        accountStore.state.value.iumrahID, accountBearerToken(),
    )

    fun defaultCareChatKey(): String? {
        val keys = state.value.sessions.filter(::isVisible).mapNotNull(::careScope)
        return if ("account" in keys) "account" else keys.firstOrNull()
    }

    fun canUseCareChat(): Boolean = defaultCareChatKey() != null

    fun careChatKey(bookingID: String): String? = if (bookingID == "account") {
        "account".takeIf { defaultCareChatKey() == "account" }
    } else booking(bookingID)?.let(::careScope)

    fun canOpenBookingFromPush(id: String): Boolean = booking(id)?.let { headersFor(it).isNotEmpty() } == true

    fun acceptsPushOwner(owner: String?, bookingID: String?): Boolean {
        val session = bookingID?.let(::booking)
        return BookingSessionPolicy.acceptsPushOwner(owner, accountStore.state.value.iumrahID,
            session?.pilgrimID ?: session?.accountOwnerID, session?.accessToken)
    }

    fun careChatKeyForPush(bookingID: String, owner: String?): String? {
        if (bookingID == "account") {
            if (owner == null) return careChatKey("account")
            if (BookingSessionPolicy.sameIdentity(owner, accountStore.state.value.iumrahID) && careChatKey("account") != null) return "account"
            return state.value.sessions.filter(::isVisible).filter { BookingSessionPolicy.sameIdentity(it.pilgrimID, owner) && careScope(it) != null }
                .singleOrNull()?.let(::careScope)
        }
        val session = booking(bookingID) ?: return null
        if (owner != null && !BookingSessionPolicy.sameIdentity(owner, session.pilgrimID)) return null
        return careScope(session)
    }

    fun careHeaders(bookingID: String): Map<String, String> {
        if (bookingID == "account") {
            if (careChatKey("account") == null) return emptyMap()
            return accountBearerToken()?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        }
        val session = booking(bookingID) ?: return emptyMap()
        if (!BookingSessionPolicy.careIsActive(session.effectiveStatus)) return emptyMap()
        // The booking-scoped route never receives an unrelated account bearer.
        return session.accessToken.trim().takeIf { it.isNotEmpty() }?.let { mapOf("x-booking-token" to it) }
            ?: if (careScope(session) == "account") headersFor(session) else emptyMap()
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

    fun headersFor(session: StoredBookingSession): Map<String, String> {
        val proof = session.accessToken.trim()
        if (proof.isNotEmpty()) return mapOf("x-booking-token" to proof)
        val owner = session.accountOwnerID ?: session.pilgrimID
        val token = accountBearerToken()?.takeIf { it.isNotBlank() }
        return if (token != null && BookingSessionPolicy.sameIdentity(owner, accountStore.state.value.iumrahID))
            mapOf("Authorization" to "Bearer $token") else emptyMap()
    }

    suspend fun create(
        journey: JourneyState,
        quote: PackageQuote,
        language: AppLanguage,
        pilgrimProfile: BookingPilgrimProfile?,
        notificationIntent: BookingCreationNotificationIntent? = null,
    ): StoredBookingSession {
        // Reject concurrent checkout submissions rather than queueing a second booking.
        check(createMutex.tryLock()) { "Booking creation is already in progress." }
        _state.update { it.copy(isMutating = true, lastError = null) }
        try {
            val quoteProof = quote.quoteProof?.trim().orEmpty()
            if (!journey.priceChangeAccepted || !quote.hasVerifiedBreakdown || !quote.allowsOccupancy(journey.trip.adults, journey.trip.rooms) || quote.bookingAllowed == false || quote.quoteId?.startsWith("server-") != true || quoteProof.isEmpty()) {
                throw IllegalStateException("The package price must be confirmed by the secure iumrah PackageEngine before booking.")
            }
            val payload = BookingDraftBuilder.make(journey, quote, language, pilgrimProfile).copy(notificationIntent = notificationIntent)
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

    /** The offer API supplies the original booking proof; never create a second package locally. */
    fun adoptOfferBooking(response: BookingCreateResponse, packageID: String, channel: String, contact: String): StoredBookingSession {
        require(BusinessOfferPolicy.validReceipt(packageID, response))
        require(channel in setOf("email", "sms") && contact.isNotBlank())
        val remote = response.booking
        val proof = requireNotNull(response.accessToken).trim()
        val existing = booking(remote.id)
        require(response.trip == null || response.trip.bookingID == remote.id)
        require(existing == null || existing.accessToken.isBlank() || existing.accessToken == proof)
        val adopted = if (existing != null && existing.accessToken.isNotBlank()) existing else {
            val initial = existing?.let { mergeRemote(it, remote).copy(accessToken = proof) }
                ?: StoredBookingSession(id = remote.id, accessToken = proof, booking = remote,
                    travelerName = remote.pilgrimProfile?.displayName, telegram = remote.pilgrimProfile?.telegram,
                    whatsapp = remote.pilgrimProfile?.whatsapp, hotelSelection = remote.hotelSelection,
                    madinahHotelSelection = remote.madinahHotelSelection)
            response.trip?.let { initial.mergeOperationalTrip(it, null, null) } ?: initial
        }
        val session = adopted.let {
            if (channel == "email") it.copy(verifiedBookingEmail = contact) else it.copy(verifiedBookingPhone = contact)
        }
        upsert(session)
        return session
    }

    suspend fun recoverGuestBooking(number:String,channel:String,challengeID:String,code:String): StoredBookingSession {
        val verified=service.confirmGuestContact(channel,challengeID,code)
        val contact=if(channel=="email") verified.email else verified.phone
        val response=service.recoverBooking(number,channel,verified.verificationToken)
        val remote=response.booking; val proof=requireNotNull(response.accessToken).trim()
        check(remote.id.isNotBlank() && proof.isNotBlank() && (response.trip==null || response.trip.bookingID==remote.id))
        val existing=booking(remote.id)
        val initial=existing?.let { mergeRemote(it,remote).copy(accessToken=proof) } ?: StoredBookingSession(remote.id,proof,remote,
            travelerName=remote.pilgrimProfile?.displayName,telegram=remote.pilgrimProfile?.telegram,whatsapp=remote.pilgrimProfile?.whatsapp,
            hotelSelection=remote.hotelSelection,madinahHotelSelection=remote.madinahHotelSelection)
        val merged=response.trip?.let { initial.mergeOperationalTrip(it) } ?: initial
        val next=if(channel=="email") merged.copy(verifiedBookingEmail=contact ?: merged.verifiedBookingEmail) else merged.copy(verifiedBookingPhone=contact ?: merged.verifiedBookingPhone)
        upsert(next)
        return next
    }

    fun adoptVerifiedBooking(id: String, expectedProof: String, link: com.iumrah.beta.models.account.IumrahAccountLinkBookingResponse) {
        val current = booking(id) ?: return
        if (current.accessToken != expectedProof || !link.ok || !BookingSessionPolicy.sameIdentity(link.pilgrimID, accountStore.state.value.iumrahID)) return
        upsert(current.copy(pilgrimID = link.pilgrimID, bookingNumber = link.bookingNumber ?: current.bookingNumber,
            bookingDisplayNumber = link.bookingDisplayNumber ?: current.bookingDisplayNumber))
    }

    fun recordVerifiedContact(id: String, expectedProof: String, channel: String, contact: String) {
        val current = booking(id) ?: return
        if (current.accessToken != expectedProof) return
        upsert(when (channel) {
            "email" -> current.copy(verifiedBookingEmail = contact)
            "sms" -> current.copy(verifiedBookingPhone = contact)
            else -> return
        })
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
        _state.update { it.copy(accountTripsRestoreState = AccountTripsRestoreState.LOADING) }
        // A failure to load the remote list is NOT proof that the account has no
        // bookings. Do not silently coerce it into an empty list.
        val trips = try {
            accountStore.accountTrips(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (accountStore.bearerToken == token) _state.update {
                it.copy(accountTripsRestoreState = AccountTripsRestoreState.FAILED)
            }
            return@withLock
        }
        if (accountStore.bearerToken != token) return@withLock
        var failedDetails = false
        for (trip in trips) {
            if (accountStore.bearerToken != token) return@withLock
            val detail = attempt { accountStore.tripDetail(trip.bookingID, token) }
            if (detail == null) { failedDetails = true; continue }
            if (accountStore.bearerToken != token) return@withLock
            if (detail.booking.id != trip.bookingID || detail.trip.bookingID != trip.bookingID) {
                failedDetails = true
                continue
            }
            val existing = booking(trip.bookingID)
            val initial = existing ?: StoredBookingSession(id = trip.bookingID, accessToken = "", booking = detail.booking, accountOwnerID = owner)
            val merged = mergeRemote(initial, detail.booking).mergeOperationalTrip(detail.trip, detail.statusHistory, detail.assignment)
            upsert(merged)
            updateESIMCache(trip.bookingID, detail.esims)
        }
        if (accountStore.bearerToken != token) return@withLock
        _state.update { current -> current.copy(accountTripsRestoreState =
            if (failedDetails && current.sessions.none(::isVisible)) AccountTripsRestoreState.FAILED else AccountTripsRestoreState.LOADED
        ) }
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

    suspend fun syncPushSubscriptions(deviceToken: String, locale: String) = pushMutex.withLock {
        val token = deviceToken.trim()
        if (token.isEmpty()) return@withLock
        val accountToken = accountStore.bearerToken
        val owner = accountStore.state.value.iumrahID
        var registeredAny = false
        var observedReady: Boolean? = null
        var firstError: String? = null
        for (session in _state.value.sessions.filter(::isVisible)) {
            if (accountStore.bearerToken != accountToken) return@withLock
            val candidates = buildList {
                session.accessToken.trim().takeIf { it.isNotEmpty() }?.let { add(mapOf("x-booking-token" to it)) }
                if (!accountToken.isNullOrBlank() && BookingSessionPolicy.sameIdentity(session.pilgrimID ?: session.accountOwnerID, owner))
                    add(mapOf("Authorization" to "Bearer $accountToken"))
            }
            if (candidates.isEmpty()) continue
            var registered = false
            var lastError: Throwable? = null
            for (headers in candidates) {
                try {
                    val response = service.registerPushDevice(session.id, headers, token, locale)
                    if (accountStore.bearerToken != accountToken || booking(session.id)?.accessToken != session.accessToken) return@withLock
                    check(response.ok && (response.bookingID == null || response.bookingID == session.id)) { "Push registration rejected" }
                    registeredAny = true
                    registered = true
                    response.ready?.let { ready -> observedReady = (observedReady ?: true) && ready }
                    break
                } catch (error: CancellationException) { throw error }
                catch (error: Throwable) {
                    lastError = error
                    if (!BookingAuthFallbackPolicy.mayRetryWithAccount(error)) break
                }
            }
            if (!registered && firstError == null) firstError = lastError?.message ?: "Push registration rejected"
        }
        _state.update { current -> current.copy(
            pushRegistrationReady = if (firstError != null) false else if (registeredAny) observedReady else null,
            pushRegistrationError = firstError,
        ) }
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
        val accountToken = accountStore.bearerToken?.takeIf { BookingSessionPolicy.sameIdentity(current.pilgrimID ?: current.accountOwnerID, accountStore.state.value.iumrahID) }
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
        check(session.canDeleteUnpaid) { "Use cancellation and refund for this booking" }
        val headers = headersFor(session)
        check(headers.isNotEmpty())
        val result = service.deleteBooking(bookingID, headers)
        check(result.deleted == true || (result.deleted == null && result.ok == true)) { "Deletion not confirmed" }
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
