package com.jakubjirak.debtaccountant.digest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DigestPeriodTest {

    private val now = Instant.parse("2026-04-17T12:00:00Z")

    @Test
    fun `last7 is exactly seven days wide`() {
        val p = DigestPeriod.Last7
        val span = p.endMillis(now) - p.startMillis(now)
        assertEquals(7L * 86_400_000L, span)
        assertEquals(now.toEpochMilli(), p.endMillis(now))
        assertTrue(p.label(now).contains("7"))
    }

    @Test
    fun `last14 and last30 compute correct spans`() {
        assertEquals(14L * 86_400_000L, DigestPeriod.Last14.endMillis(now) - DigestPeriod.Last14.startMillis(now))
        assertEquals(30L * 86_400_000L, DigestPeriod.Last30.endMillis(now) - DigestPeriod.Last30.startMillis(now))
    }

    @Test
    fun `iso week window starts on monday utc`() {
        val week = DigestPeriod.Week(2026, 16)
        val start = Instant.ofEpochMilli(week.startMillis(now))
        val end = Instant.ofEpochMilli(week.endMillis(now))
        assertEquals("2026-04-13T00:00:00Z", start.toString())
        assertEquals("2026-04-20T00:00:00Z", end.toString())
        assertTrue(week.label(now).contains("2026-W16"))
    }

    @Test
    fun `iso week rejects out-of-range weeks`() {
        assertThrows(IllegalArgumentException::class.java) { DigestPeriod.Week(2026, 0) }
        assertThrows(IllegalArgumentException::class.java) { DigestPeriod.Week(2026, 54) }
    }

    @Test
    fun `parse recognizes rolling windows and weeks`() {
        assertEquals(DigestPeriod.Last7, DigestPeriod.parse("last7"))
        assertEquals(DigestPeriod.Last14, DigestPeriod.parse("LAST14"))
        assertEquals(DigestPeriod.Last30, DigestPeriod.parse(" last30 "))
        assertEquals(DigestPeriod.Week(2026, 16), DigestPeriod.parse("week:2026-W16"))
        assertEquals(DigestPeriod.Week(2025, 1), DigestPeriod.parse("week:2025-W1"))
    }

    @Test
    fun `parse rejects bad input`() {
        assertNull(DigestPeriod.parse("daily"))
        assertNull(DigestPeriod.parse("week:bogus"))
        assertNull(DigestPeriod.parse("week:2026-W99"))
        assertNotNull(DigestPeriod.parse("week:2026-W15"))
    }
}
