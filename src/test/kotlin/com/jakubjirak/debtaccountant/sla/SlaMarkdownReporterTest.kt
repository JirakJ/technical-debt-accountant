package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SlaMarkdownReporterTest {

    private val now = Instant.parse("2026-04-17T12:00:00Z")

    private fun mkItem(
        id: String,
        priority: DebtPriority = DebtPriority.HIGH,
        ageDays: Long = 0,
        description: String = "desc",
        path: String = "src/a.kt",
    ): TechDebtItem = TechDebtItem(
        id = id,
        filePath = path,
        category = DebtCategory.values().first(),
        priority = priority,
        description = description,
        createdAt = now.minusSeconds(ageDays * 86_400).toEpochMilli(),
        updatedAt = now.toEpochMilli(),
    )

    private val policy: SlaPolicy = SlaPolicy.DEFAULT

    @Test
    fun `green banner when healthy`() {
        val eval = SlaEvaluator.evaluate(listOf(mkItem("a", ageDays = 1)), policy, now)
        val md = SlaMarkdownReporter.render(eval, "demo")
        assertTrue(md.contains("🟢"), md)
        assertFalse(md.contains("## 🔴 Breached"))
        assertFalse(md.contains("## 🟡 Approaching"))
    }

    @Test
    fun `yellow banner when approaching present`() {
        val eval = SlaEvaluator.evaluate(listOf(mkItem("a", ageDays = 25)), policy, now)
        val md = SlaMarkdownReporter.render(eval, "demo")
        assertTrue(md.contains("🟡"))
        assertTrue(md.contains("## 🟡 Approaching"))
    }

    @Test
    fun `red banner when breached present`() {
        val eval = SlaEvaluator.evaluate(listOf(mkItem("a", ageDays = 60)), policy, now)
        val md = SlaMarkdownReporter.render(eval, "demo")
        assertTrue(md.contains("🔴"))
        assertTrue(md.contains("## 🔴 Breached"))
    }

    @Test
    fun `markdown specials in title and path are escaped`() {
        val item = mkItem(
            id = "x",
            ageDays = 60,
            description = "fix |pipe| and *stars* and _under_",
            path = "src/a|b.kt",
        )
        val eval = SlaEvaluator.evaluate(listOf(item), policy, now)
        val md = SlaMarkdownReporter.render(eval, "p|roj")
        assertTrue(md.contains("\\|"), "pipe must be escaped")
        assertTrue(md.contains("\\*stars\\*"))
        assertTrue(md.contains("\\_under\\_"))
    }

    @Test
    fun `empty sections are suppressed`() {
        val eval = SlaEvaluator.evaluate(emptyList(), policy, now)
        val md = SlaMarkdownReporter.render(eval, "demo")
        assertFalse(md.contains("## 🔴 Breached"))
        assertFalse(md.contains("## 🟡 Approaching"))
        assertFalse(md.contains("## ⬆️ Escalations"))
    }

    @Test
    fun `breached table has header row with all columns`() {
        val eval = SlaEvaluator.evaluate(listOf(mkItem("a", ageDays = 60)), policy, now)
        val md = SlaMarkdownReporter.render(eval, "demo")
        val header = "| ID | Description | Priority | Age (days) | Over by | Owner | Path |"
        assertTrue(md.contains(header), "missing header:\n$md")
    }

    @Test
    fun `escape helper handles backtick and backslash`() {
        assertEquals("a\\`b", SlaMarkdownReporter.escape("a`b"))
        assertEquals("a\\\\b", SlaMarkdownReporter.escape("a\\b"))
    }

    @Test
    fun `traffic light helper - all three cases`() {
        val ok = SlaEvaluator.evaluate(emptyList(), policy, now)
        assertTrue(SlaMarkdownReporter.trafficLight(ok).contains("🟢"))

        val warn = SlaEvaluator.evaluate(listOf(
            TechDebtItem(
                id = "w", filePath = "x", category = DebtCategory.values().first(),
                priority = DebtPriority.HIGH, description = "d",
                createdAt = now.minusSeconds(25 * 86_400).toEpochMilli(),
                updatedAt = now.toEpochMilli(),
            )
        ), policy, now)
        assertTrue(SlaMarkdownReporter.trafficLight(warn).contains("🟡"))

        val red = SlaEvaluator.evaluate(listOf(
            TechDebtItem(
                id = "r", filePath = "x", category = DebtCategory.values().first(),
                priority = DebtPriority.HIGH, description = "d",
                createdAt = now.minusSeconds(60 * 86_400).toEpochMilli(),
                updatedAt = now.toEpochMilli(),
            )
        ), policy, now)
        assertTrue(SlaMarkdownReporter.trafficLight(red).contains("🔴"))
    }

    @Test
    fun `escalations section rendered when present`() {
        val eval = SlaEvaluator.evaluate(listOf(mkItem("a", ageDays = 60)), policy, now)
        assertTrue(eval.escalations.isNotEmpty())
        val md = SlaMarkdownReporter.render(eval, "demo")
        assertTrue(md.contains("## ⬆️ Escalations"))
        assertTrue(md.contains("High → Critical"))
    }
}
