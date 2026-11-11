package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownDigestRendererTest {

    private val now = 1_713_355_200_000L

    private fun sample(previous: DigestSnapshot? = null): DigestData = DigestDataCollector(
        valuator = { it.estimatedFixMinutes.toDouble() },
        monthlyInterestValuator = { it.totalWorkaroundMinutes.toDouble() },
        slaProvider = SlaSummaryProvider { "BREACHED" }
    ).collect(
        listOf(
            item("a", priority = DebtPriority.HIGH, filePath = "src/Alpha.kt", estimatedFixMinutes = 120, workaroundMinutes = 60),
            item("b|bad", priority = DebtPriority.CRITICAL, filePath = "src/Beta.kt", estimatedFixMinutes = 60, workaroundMinutes = 30),
            item(
                "c", priority = DebtPriority.LOW, filePath = "src/Gamma.kt", estimatedFixMinutes = 30,
                resolved = true, resolvedAtMillis = now - 3600_000
            )
        ),
        previous,
        DigestPeriod.Last7,
        "Demo",
        now
    )

    @Test
    fun `contains all required sections`() {
        val md = MarkdownDigestRenderer.render(sample())
        assertTrue(md.contains("# Weekly Digest — Demo"))
        assertTrue(md.contains("## Summary"))
        assertTrue(md.contains("## Priority Breakdown"))
        assertTrue(md.contains("## SLA Compliance"))
        assertTrue(md.contains("## Top Hotspots"))
        assertTrue(md.contains("## Sprint Recommendations"))
        assertFalse(md.contains("## Week-over-Week Delta"), "No delta section when previous is null")
    }

    @Test
    fun `escapes pipes in descriptions and paths`() {
        val md = MarkdownDigestRenderer.render(sample())
        assertTrue(md.contains("b\\|bad"))
    }

    @Test
    fun `totals and counts surface in markdown`() {
        val md = MarkdownDigestRenderer.render(sample())
        assertTrue(md.contains("Active debt items | 2"))
        assertTrue(md.contains("Total estimated cost | $180.00"))
        assertTrue(md.contains("Resolved in period | 1"))
    }

    @Test
    fun `delta section rendered with signed values`() {
        val prev = DigestSnapshot(
            takenAtMillis = now - 7L * 86_400_000,
            label = "Last week",
            items = listOf(
                TechDebtItemSnapshot.from(item("a", priority = DebtPriority.HIGH), 100.0, "OK")
            )
        )
        val md = MarkdownDigestRenderer.render(sample(prev))
        assertTrue(md.contains("## Week-over-Week Delta"))
        assertTrue(md.contains("Last week"))
        assertTrue(md.contains("| +1 |") || md.contains("| -1 |") || md.contains("| 0 |"))
    }

    @Test
    fun `empty data produces no hotspots notice`() {
        val data = DigestDataCollector(valuator = { 0.0 }).collect(emptyList(), null, DigestPeriod.Last7, "Empty", now)
        val md = MarkdownDigestRenderer.render(data)
        assertTrue(md.contains("No active debt"))
        assertTrue(md.contains("No ROI-ranked recommendations"))
    }

    @Test
    fun `sla worst breach displayed when breaches present`() {
        val data = sample()
        val md = MarkdownDigestRenderer.render(data)
        assertTrue(md.contains("Worst breach"))
        assertEquals(2, data.sla.breached)
    }
}
