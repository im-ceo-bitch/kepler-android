package com.iumrah.beta.data.chat

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.domain.booking.BookingAuthFallbackPolicy
import com.iumrah.beta.models.booking.ChatListResponse
import com.iumrah.beta.models.booking.ChatMessage
import com.iumrah.beta.models.booking.ChatMessagePostResponse
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Serializable
data class IumrahPublicProfile(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val roleKind: String = "",
    val roleTitle: String = "",
    val phoneUZ: String = "",
    val phoneSA: String = "",
    val telegram: String = "",
    val whatsapp: String = "",
    val instagram: String = "",
    val bio: String = "",
    val publicSlug: String = "",
    val publicVisible: Boolean = false,
    val active: Boolean = true,
    val isOwner: Boolean = false,
    val photoURL: String? = null,
)

@Serializable private data class ChatMessageSendRequest(val body: String, val clientMessageID: String)
@Serializable private class EmptyPayload
@Serializable private data class BasicOKResponse(val ok: Boolean)
@Serializable private data class IumrahPublicProfilesResponse(val ok: Boolean, val members: List<IumrahPublicProfile>)

class ChatService(private val api: APIClient) {
    // Like iOS, try booking proof alone; fall back to account only for auth/not-found.
    private suspend fun <T> authorized(headers: Map<String, String>, action: suspend (Map<String, String>) -> T): T {
        val booking = headers["x-booking-token"]?.takeIf { it.isNotBlank() }
        val account = headers["Authorization"]?.takeIf { it.isNotBlank() }
        if (booking == null && account == null) throw APIException.MissingBookingToken
        if (booking == null) return action(mapOf("Authorization" to account!!))
        try { return action(mapOf("x-booking-token" to booking)) }
        catch (error: APIException) {
            if (account == null || !BookingAuthFallbackPolicy.mayRetryWithAccount(error)) throw error
            return action(mapOf("Authorization" to account))
        }
    }
    private data class ChatSnapshot(val messages: List<ChatMessage>, val cursor: String = "0", val scopeVersion: String? = null)
    private val cache = object : LinkedHashMap<String, ChatSnapshot>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChatSnapshot>?) = size > 12
    }
    private val loadMutex = Mutex()
    private var cacheGeneration = 0L
    private val revision = MutableStateFlow(0L)
    val refreshRevision = revision.asStateFlow()
    fun requestRefresh() { revision.update { it + 1 } }
    @Synchronized fun cachedChat(bookingID: String, headers: Map<String, String>): List<ChatMessage> =
        ChatDelta.scope(bookingID, headers)?.let { cache[it]?.messages }.orEmpty()
    private fun mergeMessages(old: List<ChatMessage>, fresh: List<ChatMessage>, readIDs: Set<String> = emptySet()): List<ChatMessage> {
        val alreadyRead = old.filter { it.readByStaff == true }.mapTo(hashSetOf()) { it.id } + readIDs
        return ChatDelta.merge(old, fresh, ChatMessage::id, ChatMessage::createdAt).map {
            if (it.id in alreadyRead && it.readByStaff != true) it.copy(readByStaff = true) else it
        }
    }
    @Synchronized fun mergeChat(bookingID: String, headers: Map<String, String>, fresh: List<ChatMessage>): List<ChatMessage> {
        val key = ChatDelta.scope(bookingID, headers) ?: return emptyList()
        val previous = cache[key] ?: ChatSnapshot(emptyList())
        val result = mergeMessages(previous.messages, fresh.filter { it.bookingID == bookingID })
        cache[key] = previous.copy(messages = result)
        return result
    }
    @Synchronized fun clearCache() { cacheGeneration++; cache.clear() }
    suspend fun loadChat(bookingID: String, headers: Map<String, String>): List<ChatMessage> {
        val key = ChatDelta.scope(bookingID, headers) ?: throw APIException.MissingBookingToken
        val generation = synchronized(this) { cacheGeneration }
        return loadMutex.withLock {
            repeat(20) {
                currentCoroutineContext().ensureActive()
                val previous = synchronized(this) {
                    if (generation != cacheGeneration) throw CancellationException("Chat authorization changed")
                    cache[key] ?: ChatSnapshot(emptyList())
                }
                val after = previous.cursor
                val unread = previous.messages.filter { it.readByStaff != true && (it.senderType.equals("client", true) || it.senderType.equals("pilgrim", true)) }.map { it.id }
                val response = authorized(headers) { proof -> api.get<ChatListResponse>(ChatDelta.path(bookingID, after, unread, previous.scopeVersion), headers = proof) }
                currentCoroutineContext().ensureActive()
                synchronized(this) {
                    if (generation != cacheGeneration) throw CancellationException("Chat authorization changed")
                    val current = cache[key] ?: previous
                    val reset = response.reset == true || (previous.scopeVersion != null && response.scopeVersion != null && previous.scopeVersion != response.scopeVersion)
                    val messages = mergeMessages(if (reset) emptyList() else current.messages,
                        response.messages.filter { it.bookingID == bookingID }, response.staffReadMessageIDs.orEmpty().toSet())
                    cache[key] = ChatSnapshot(messages, response.nextCursor ?: "0", response.scopeVersion)
                }
                if (response.hasMore != true || response.nextCursor == null || (response.nextCursor == after && response.reset != true)) return@withLock cachedChat(bookingID, headers)
            }
            cachedChat(bookingID, headers)
        }
    }

    suspend fun send(message: String, bookingID: String, headers: Map<String, String>, clientMessageID: String = UUID.randomUUID().toString()): ChatMessage =
        authorized(headers) { proof -> api.post<ChatMessagePostResponse, ChatMessageSendRequest>(
            "/api/catalog/hotels/client/chats/$bookingID/messages",
            ChatMessageSendRequest(message.trim(), clientMessageID),
            proof,
        ).message }

    suspend fun sendPhoto(data: ByteArray, bookingID: String, headers: Map<String, String>): ChatMessage =
        authorized(headers) { proof -> api.upload<ChatMessagePostResponse>(
            "/api/catalog/hotels/client/chats/$bookingID/attachments",
            data,
            "image/jpeg",
            proof,
            75,
        ).message }

    suspend fun loadAttachment(path: String, headers: Map<String, String>): ByteArray = authorized(headers) { proof -> api.fetchData(path, proof, 45) }

    suspend fun markRead(bookingID: String, headers: Map<String, String>) {
        authorized(headers) { proof -> api.post<BasicOKResponse, EmptyPayload>(
            "/api/catalog/hotels/client/chats/$bookingID/read",
            EmptyPayload(),
            proof,
        ) }
    }

    suspend fun loadTeamProfiles(): List<IumrahPublicProfile> {
        val value = api.get<IumrahPublicProfilesResponse>("/api/catalog/hotels/team")
        return value.members.filter { it.active && it.publicVisible }
    }

    suspend fun loadCareProfile(): IumrahPublicProfile? {
        val members = loadTeamProfiles()
        return members.firstOrNull { it.isOwner } ?: members.firstOrNull()
    }

    suspend fun loadTeamProfile(id: String): IumrahPublicProfile? =
        loadTeamProfiles().firstOrNull { it.id == id }
}
