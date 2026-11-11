package com.jakubjirak.debtaccountant.report

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import com.jakubjirak.debtaccountant.valuation.RoiCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportGeneratorTest {

    private val now = 1_800_000_000_000L
    private val valuator = DollarValuator(hourlyRateProvider = { 60.0 }, currencyProvider = { "USD" }, clock = { now })
    private val generator = ReportGenerator(valuator)

    @Suppress("LongParameterList") // test fixture builder: every parameter has a default
    private fun item(
        id: String,
        file: String = "src/A.kt",
        priority: DebtPriority = DebtPriority.MEDIUM,
        category: DebtCategory = DebtCategory.CODE_SMELL,
        fixMinutes: Int = 60,
        recentWorkaround: Int = 0,
        resolved: Boolean = false,
    ) = TechDebtItem(
        id = id,
        filePath = file,
        category = category,
        priority = priority,
        description = "item $id",
        estimatedFixMinutes = fixMinutes,
        workaroundLogs = if (recentWorkaround > 0) {
            mutableListOf(WorkaroundLog(id = "w$id", timestamp = now - 1000, durationMinutes = recentWorkaround))
        } else {
            mutableListOf()
        },
        createdAt = now,
        updatedAt = now,
        resolved = resolved,
    )

    private val items = listOf(
        item("a1", priority = DebtPriority.CRITICAL, category = DebtCategory.SECURITY_VULNERABILITY, recentWorkaround = 30),
        item("b2", file = "src/B.kt", priority = DebtPriority.LOW, fixMinutes = 30, recentWorkaround = 30),
        item("c3", file = "src/C.kt", resolved = true, fixMinutes = 600, recentWorkaround = 600),
    )

    @Test
    fun `report counts active and resolved items`() {
        val report = generator.generateReport("demo", items, "USD")
        assertEquals("demo", report.projectName)
        assertEquals(3, report.totalItems)
        assertEquals(1, report.resolvedItems)
        assertEquals(2, report.activeItems)
    }

    @Test
    fun `costs only include active items`() {
        val report = generator.generateReport("demo", items, "USD")
        assertEquals(60.0, report.totalWorkaroundCost, 1e-9)
        assertEquals(90.0, report.totalFixCost, 1e-9)
    }

    @Test
    fun `breakdowns group active items`() {
        val report = generator.generateReport("demo", items, "USD")
        assertEquals(mapOf(DebtPriority.CRITICAL to 1, DebtPriority.LOW to 1), report.priorityBreakdown)
        assertEquals(
            mapOf(DebtCategory.SECURITY_VULNERABILITY to 1, DebtCategory.CODE_SMELL to 1),
            report.categoryBreakdown
        )
    }

    @Test
    fun `roi opportunities are ordered by break-even and exclude resolved items`() {
        val report = generator.generateReport("demo", items, "USD")
        assertEquals(listOf("b2", "a1"), report.topRoiOpportunities.map { it.itemId })
    }

    @Test
    fun `empty inventory produces a healthy empty report`() {
        val report = generator.generateReport("empty", emptyList(), "EUR")
        assertEquals(0, report.totalItems)
        assertEquals(100, report.healthScore)
        assertEquals("A", report.healthGrade)
        assertTrue(report.topHotspots.isEmpty())
        assertTrue(report.topRoiOpportunities.isEmpty())
    }

    @Test
    fun `markdown contains summary and populated sections`() {
        val md = generator.toMarkdown(generator.generateReport("demo", items, "USD"))
        assertTrue(md.startsWith("# Technical Debt Report — demo"))
        assertTrue(md.contains("## Executive Summary"))
        assertTrue(md.contains("| Active Debt Items | 2 |"))
        assertTrue(md.contains("## Priority Breakdown"))
        assertTrue(md.contains("| Critical | 1 |"))
        assertTrue(md.contains("## Category Breakdown"))
        assertTrue(md.contains("## Top Debt Hotspots"))
        assertTrue(md.contains("## Top ROI Opportunities"))
        assertTrue(md.contains("src/A.kt"))
    }

    @Test
    fun `markdown omits empty sections`() {
        val md = generator.toMarkdown(generator.generateReport("empty", emptyList(), "USD"))
        assertFalse(md.contains("## Priority Breakdown"))
        assertFalse(md.contains("## Top Debt Hotspots"))
        assertFalse(md.contains("## Top ROI Opportunities"))
    }

    @Test
    fun `roi calculator drops items without recurring interest and honours limit`() {
        val roi = RoiCalculator(valuator)
        val ranked = roi.prioritizeByRoi(items + item("d4", recentWorkaround = 0))
        assertFalse(ranked.any { it.itemId == "d4" })
        assertEquals(1, roi.getTopRoiOpportunities(items, limit = 1).size)
    }
}
