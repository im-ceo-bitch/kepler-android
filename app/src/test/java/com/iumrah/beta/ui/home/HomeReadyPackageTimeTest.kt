package com.iumrah.beta.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class HomeReadyPackageTimeTest {
    @Test fun `ISO offset and UTC are formatted in device time zone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"))
            assertEquals("15:30", homeFlightTime("2026-10-12T10:30:00Z"))
            assertEquals("12 Oct", homeFlightDay("2026-10-12T10:30:00Z"))
            assertEquals("15:30", homeFlightTime("2026-10-12T13:30:00+03:00"))
            assertEquals("15:30", homeFlightTime("2026-10-12T10:30:00.123Z"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun `unverified date strings do not invent departure times`() {
        assertEquals("—", homeFlightTime("2026-10-12"))
        assertEquals("2026-10-12", homeFlightDay("2026-10-12"))
        assertEquals("—", homeFlightTime("tomorrow"))
        assertEquals("—", homeFlightDay("bad data"))
    }
}
