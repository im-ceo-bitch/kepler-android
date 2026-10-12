package com.iumrah.beta.data.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AviasalesSearchLinkBuilderTest {
    @Test fun `one-way route search uses supplier calendar day not timezone conversion`() {
        assertEquals(
            "https://www.aviasales.ru/search/TAS1210JED1",
            AviasalesSearchLinkBuilder.searchUrl(" tas ", "jed", "2026-10-12T23:40:00+05:00", null, 1, 0, 0),
        )
    }

    @Test fun `round-trip search correctly includes passenger counts`() {
        assertEquals(
            "https://www.aviasales.ru/search/TAS1210MED2010211",
            AviasalesSearchLinkBuilder.searchUrl("TAS", "MED", "2026-10-12", "2026-10-20", 2, 1, 1),
        )
    }

    @Test fun `invalid dates codes and past returns are not sent to seller`() {
        assertNull(AviasalesSearchLinkBuilder.searchUrl("TAS", "JED", "2026-02-30", null, 1, 0, 0))
        assertNull(AviasalesSearchLinkBuilder.searchUrl("TAS", "JED", "2026-10-20", "2026-10-10", 1, 0, 0))
        assertNull(AviasalesSearchLinkBuilder.searchUrl("TAS", "TAS", "2026-10-12", null, 1, 0, 0))
        assertNull(AviasalesSearchLinkBuilder.searchUrl("TA5", "JED", "2026-10-12", null, 1, 0, 0))
        assertNull(AviasalesSearchLinkBuilder.searchUrl("TAS", "JED", "2026-10-12", null, 0, 0, 0))
    }
}
