package com.iumrah.beta.data.chat

object ChatDeltaRegression {
    data class Message(val id: String, val time: String, val read: Boolean = false)
    @JvmStatic fun main(args: Array<String>) {
        val original = listOf(Message("a", "2026-11-14T10:00:00Z"), Message("b", "2026-11-14T10:01:00Z"))
        val merged = ChatDelta.merge(original, listOf(original[0].copy(read = true), Message("c", "2026-11-14T10:01:00Z")), Message::id, Message::time)
        check(merged.map { it.id } == listOf("a", "b", "c"))
        check(merged[0].read)
        check(ChatDelta.merge(merged, emptyList(), Message::id, Message::time) == merged)
        check(ChatDelta.path("booking", "2026-11-14T10:00:00Z", listOf("a", "b&c")) == "/api/catalog/hotels/client/chats/booking/messages?after=2026-11-14T10%3A00%3A00Z&unread=a&unread=b%26c")
        check(ChatDelta.path("booking", "", (1..100).map { it.toString() }).split("&unread=").size == 81)
        check(ChatDelta.scope("booking", emptyMap()) == null)
        check(ChatDelta.scope("a", mapOf("Authorization" to "one")) != ChatDelta.scope("a", mapOf("Authorization" to "two")))
        check(ChatDelta.scope("a", mapOf("Authorization" to "one")) != ChatDelta.scope("b", mapOf("Authorization" to "one")))
        check(ChatDelta.scope("a", mapOf("Authorization" to "secret-token"))?.contains("secret-token") == false)
        println("ChatDelta: 9 checks passed")
    }
}
