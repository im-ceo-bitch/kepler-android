package com.iumrah.beta.data.notification
import java.security.MessageDigest
/** Reject obsolete responses even when a user returns to the previous account. */
internal class NotificationScopeGate(initialScope: String) {
    data class Ticket(val scope: String, val generation: Long, val revision: Long)
    var scope: String = initialScope
        private set
    private var generation = 0L
    private var registration = 0L
    private var feed = 0L
    fun switch(next: String): Boolean {
        if (scope == next) return false
        scope = next; generation++; registration++; feed++
        return true
    }
    fun register() = Ticket(scope, generation, ++registration)
    fun refresh() = Ticket(scope, generation, ++feed)
    fun registrationCurrent(ticket: Ticket, live: String) = sameScope(ticket, live) && ticket.revision == registration
    fun feedCurrent(ticket: Ticket, live: String) = sameScope(ticket, live) && ticket.revision == feed
    private fun sameScope(ticket: Ticket, live: String) = ticket.scope == scope && live == scope && ticket.generation == generation
    companion object {
        fun fingerprint(token: String?, tripIDs: List<String>): String {
            val raw = listOf(token.orEmpty()) + tripIDs.distinct().sorted()
            return MessageDigest.getInstance("SHA-256").digest(raw.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
