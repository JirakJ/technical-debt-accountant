package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DigestDataCollectorTest {

    private val now = 1_713_355_200_000L // 2024-04-17T12:00:00Z

    private fun collector(
        valuator: (com.jakubjirak.debtaccountant.model.TechDebtItem) -> Double = { (it.estimatedFixMinutes).toDouble() },
        monthly: (com.jakubjirak.debtaccountant.model.TechDebtItem) -> Double = { it.totalWorkaroundMinutes.toDouble() },
        slaProvider: SlaSummaryProvider = SlaSummaryProvider { "OK" }
    ) = DigestDataCollector(valuator, monthly, slaProvider)

    @Test
    fun `summary counts only active items`() {
        val items = listOf(
            item("a", createdAtMillis = now - 86_400_000L, priority = DebtPriority.HIGH),
            item("b", createdAtMillis = now - 2 * 86_400_000L, priority = DebtPriority.LOW),
            item("c", createdAtMillis = now - 3 * 86_400_000L, resolved = true, resolvedAtMillis = now - 86_400_000L)
        )
        val data = collector().collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals(2, data.totalItemCount)
        assertEquals(1, data.priorityBreakdown[DebtPriority.HIGH])
        assertEquals(1, data.priorityBreakdown[DebtPriority.LOW])
        assertEquals(0, data.priorityBreakdown[DebtPriority.CRITICAL])
    }

    @Test
    fun `new and resolved counts respect period window`() {
        val items = listOf(
            item("fresh", createdAtMillis = now - 86_400_000L),
            item("old", createdAtMillis = now - 30L * 86_400_000L),
            item("resolvedNow", createdAtMillis = now - 40L * 86_400_000L,
                resolved = true, resolvedAtMillis = now - 86_400_000L)
        )
        val data = collector().collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals(1, data.newInPeriod)
        assertEquals(1, data.resolvedInPeriod)
    }

    @Test
    fun `hotspots sorted by dollar value desc`() {
        val items = listOf(
            item("a", filePath = "src/A.kt", estimatedFixMinutes = 100, createdAtMillis = now),
            item("b", filePath = "src/A.kt", estimatedFixMinutes = 200, createdAtMillis = now),
            item("c", filePath = "src/B.kt", estimatedFixMinutes = 50, createdAtMillis = now)
        )
        val data = collector().collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals("src/A.kt", data.hotspots.first().filePath)
        assertEquals(300.0, data.hotspots.first().totalDollarValue)
        assertEquals(2, data.hotspots.first().itemCount)
    }

    @Test
    fun `recommendations ranked by roi then priority then id`() {
        val items = listOf(
            item("low", estimatedFixMinutes = 60, workaroundMinutes = 0, priority = DebtPriority.LOW),
            item("high-roi", estimatedFixMinutes = 60, workaroundMinutes = 120, priority = DebtPriority.HIGH),
            item("crit", estimatedFixMinutes = 60, workaroundMinutes = 30, priority = DebtPriority.CRITICAL)
        )
        val data = collector().collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals("high-roi", data.recommendations.first().itemId)
    }

    @Test
    fun `sla summary reflects provider classification`() {
        val items = listOf(item("a"), item("b"), item("c"))
        val mapping = mapOf("a" to "OK", "b" to "APPROACHING", "c" to "BREACHED")
        val data = DigestDataCollector(
            valuator = { 10.0 },
            slaProvider = SlaSummaryProvider { mapping[it.id]!! }
        ).collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals(1, data.sla.ok)
        assertEquals(1, data.sla.approaching)
        assertEquals(1, data.sla.breached)
        assertEquals(3, data.sla.total)
    }

    @Test
    fun `delta computed against previous snapshot`() {
        val prev = DigestSnapshot(
            takenAtMillis = now - 7L * 86_400_000,
            label = "previous",
            items = listOf(
                TechDebtItemSnapshot.from(item("a", priority = DebtPriority.HIGH), 100.0, "OK"),
                TechDebtItemSnapshot.from(item("b", priority = DebtPriority.LOW), 50.0, "OK")
            )
        )
        val current = listOf(
            item("a", priority = DebtPriority.HIGH, estimatedFixMinutes = 100),
            item("c", priority = DebtPriority.CRITICAL, estimatedFixMinutes = 200)
        )
        val data = collector({ it.estimatedFixMinutes.toDouble() }).collect(current, prev, DigestPeriod.Last7, "proj", now)
        assertNotNull(data.delta)
        val delta = data.delta!!
        assertEquals(0, delta.itemCountDelta)
        assertEquals(1, delta.priorityMixDelta[DebtPriority.CRITICAL])
        assertEquals(-1, delta.priorityMixDelta[DebtPriority.LOW])
        assertEquals("previous", delta.previousLabel)
        assertEquals(1, delta.newlyResolvedSincePrevious)
    }

    @Test
    fun `no delta when previous snapshot absent`() {
        val data = collector().collect(listOf(item("a")), null, DigestPeriod.Last7, "proj", now)
        assertNull(data.delta)
    }

    @Test
    fun `valuator failures clamped to zero only through monthly`() {
        val items = listOf(item("a", estimatedFixMinutes = 60))
        val data = DigestDataCollector(
            valuator = { 42.5 },
            monthlyInterestValuator = { 0.0 }
        ).collect(items, null, DigestPeriod.Last7, "proj", now)
        assertEquals(42.5, data.totalDollarValue)
        assertTrue(data.recommendations.first().roi == 0.0)
    }
}
