package com.iumrah.beta.domain.journey

import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.TransferVehicleKind
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TransferQuoteConcurrencyTest {
    private fun quote(id: String, allowed: Boolean = true) = PackageQuote(
        totalPackagePrice = BigDecimal("2500"), pricePerPerson = BigDecimal("1250"),
        currency = "USD", isEstimated = false, quoteId = "server-$id", quoteProof = "proof-$id", bookingAllowed = allowed,
    )

    @Test fun changedVehicleRejectsOldResponse() = runBlocking {
        val store = JourneyStore()
        store.chooseTransferVehicle(TransferVehicleKind.CARNIVAL)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<PackageQuote>()
        val old = async { runCatching { store.refreshTransferQuote { started.complete(Unit); response.await() } } }
        started.await()
        store.chooseTransferVehicle(TransferVehicleKind.YUKON)
        val newest = store.refreshTransferQuote { quote("vip") }
        response.complete(quote("old"))
        assertTrue(old.await().isFailure)
        assertEquals(newest, store.state.value.quote)
    }

    @Test fun olderResponseCannotWinEvenForSameInputs() = runBlocking {
        val store = JourneyStore()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<PackageQuote>()
        val old = async { runCatching { store.refreshTransferQuote { started.complete(Unit); response.await() } } }
        started.await()
        val latest = store.refreshTransferQuote { quote("latest") }
        response.complete(quote("old"))
        assertTrue(old.await().isFailure)
        assertEquals(latest, store.state.value.quote)
    }

    @Test fun forbiddenOrUnsignedQuoteClearsCurrentPrice() = runBlocking {
        val store = JourneyStore()
        store.refreshTransferQuote { quote("valid") }
        assertTrue(runCatching { store.refreshTransferQuote { quote("denied", false) } }.isFailure)
        assertNull(store.state.value.quote)
        assertTrue(runCatching { store.refreshTransferQuote { quote("unsigned").copy(quoteProof = null) } }.isFailure)
        assertNull(store.state.value.quote)
    }
}
