package com.iumrah.beta.data.hotel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorefrontCachePolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun `a recent public hotel list is allowed offline`() {
        assertTrue(catalogCacheIsFresh(now - 1_000L, now))
        assertTrue(catalogCacheIsFresh(now, now))
    }

    @Test fun `public hotel detail expires after seven days`() {
        assertTrue(hotelDetailCacheIsFresh(now - 2L * 24 * 60 * 60 * 1_000, now))
        assertFalse(hotelDetailCacheIsFresh(now - 8L * 24 * 60 * 60 * 1_000, now))
        assertFalse(hotelDetailCacheIsFresh(now + 1L, now))
    }

    @Test fun `expired future dated and invalid snapshots are rejected`() {
        assertFalse(catalogCacheIsFresh(now - 49L * 60 * 60 * 1_000, now))
        assertFalse(catalogCacheIsFresh(now + 1L, now))
        assertFalse(catalogCacheIsFresh(0L, now))
        assertFalse(catalogCacheIsFresh(-5L, now))
    }
}
