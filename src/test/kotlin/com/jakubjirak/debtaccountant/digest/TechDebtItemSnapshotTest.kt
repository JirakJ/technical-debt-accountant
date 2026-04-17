package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TechDebtItemSnapshotTest {

    @Test
    fun `from copies identifying fields`() {
        val src = item(
            id = "x1",
            priority = DebtPriority.HIGH,
            estimatedFixMinutes = 42,
            workaroundMinutes = 10,
            tags = setOf("z", "a"),
            author = "alice"
        )
        val snap = TechDebtItemSnapshot.from(src, dollarValue = 99.5, slaStatus = "APPROACHING")
        assertEquals("x1", snap.id)
        assertEquals(DebtPriority.HIGH, snap.priority)
        assertEquals(42, snap.estimatedFixMinutes)
        assertEquals(10, snap.totalWorkaroundMinutes)
        assertEquals(listOf("a", "z"), snap.tags)
        assertEquals("alice", snap.author)
        assertEquals(99.5, snap.dollarValue)
        assertEquals("APPROACHING", snap.slaStatus)
    }

    @Test
    fun `resolved propagates from isResolved helper`() {
        val src = item(id = "y", resolved = true, resolvedAtMillis = 123L)
        val snap = TechDebtItemSnapshot.from(src)
        assertTrue(snap.resolved)
        assertEquals(123L, snap.resolvedAt)
    }
}
