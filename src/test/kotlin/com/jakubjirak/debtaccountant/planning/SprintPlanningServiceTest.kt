package com.jakubjirak.debtaccountant.planning

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SprintPlanningServiceTest {

    private val service = SprintPlanningService()

    private fun item(
        id: String,
        priority: DebtPriority = DebtPriority.MEDIUM,
        minutes: Int = 60,
        tags: Set<String> = emptySet(),
        resolved: Boolean = false,
        description: String = "desc",
        filePath: String = "src/a.kt",
        createdAt: Long = 1_000L
    ): TechDebtItem = TechDebtItem(
        id = id,
        filePath = filePath,
        category = DebtCategory.values().first(),
        priority = priority,
        description = description,
        estimatedFixMinutes = minutes,
        tags = tags.toMutableSet(),
        resolved = resolved,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun opts(
        budgetHours: Double,
        include: Set<String> = emptySet(),
        exclude: Set<String> = emptySet(),
        min: DebtPriority = DebtPriority.LOW,
        overflow: Boolean = false,
        valuator: (TechDebtItem) -> Double = { 10.0 }
    ) = SprintPlanningOptions(
        budgetHours = budgetHours,
        tagsIncluded = include,
        tagsExcluded = exclude,
        minPriority = min,
        allowOverflowOnce = overflow,
        valuator = valuator
    )

    @Test
    fun `empty inventory returns empty plan`() {
        val plan = service.plan(emptyList(), opts(8.0))
        assertEquals(0, plan.itemCount)
        assertEquals(0, plan.declined.size)
        assertEquals(0.0, plan.totalHours)
        assertEquals(0.0, plan.totalMonthlyDollarSavings)
        assertEquals(8.0, plan.budgetHours)
    }

    @Test
    fun `items with zero effort are declined as NO_EFFORT`() {
        val a = item("a", minutes = 0)
        val b = item("b", minutes = -5)
        val c = item("c", minutes = 60)
        val plan = service.plan(listOf(a, b, c), opts(8.0))
        val reasons = plan.declined.associate { it.item.id to it.reason }
        assertEquals(DeclineReason.NO_EFFORT, reasons["a"])
        assertEquals(DeclineReason.NO_EFFORT, reasons["b"])
        assertEquals(1, plan.selected.size)
        assertEquals("c", plan.selected[0].item.id)
    }

    @Test
    fun `tag include filter excludes non-matching`() {
        val a = item("a", tags = setOf("ui"))
        val b = item("b", tags = setOf("backend"))
        val plan = service.plan(listOf(a, b), opts(8.0, include = setOf("ui")))
        assertEquals(listOf("a"), plan.selected.map { it.item.id })
        assertEquals(DeclineReason.FILTERED, plan.declined.single { it.item.id == "b" }.reason)
    }

    @Test
    fun `tag exclude filter removes matches`() {
        val a = item("a", tags = setOf("ui"))
        val b = item("b", tags = setOf("backend"))
        val plan = service.plan(listOf(a, b), opts(8.0, exclude = setOf("ui")))
        assertEquals(listOf("b"), plan.selected.map { it.item.id })
        assertEquals(DeclineReason.FILTERED, plan.declined.single { it.item.id == "a" }.reason)
    }

    @Test
    fun `min priority filters out lower priority items`() {
        val a = item("a", priority = DebtPriority.LOW)
        val b = item("b", priority = DebtPriority.HIGH)
        val plan = service.plan(listOf(a, b), opts(8.0, min = DebtPriority.HIGH))
        assertEquals(listOf("b"), plan.selected.map { it.item.id })
        assertEquals(DeclineReason.FILTERED, plan.declined.single { it.item.id == "a" }.reason)
    }

    @Test
    fun `resolved items are filtered out`() {
        val a = item("a", resolved = true)
        val b = item("b")
        val plan = service.plan(listOf(a, b), opts(8.0))
        assertEquals(listOf("b"), plan.selected.map { it.item.id })
        assertEquals(DeclineReason.FILTERED, plan.declined.single { it.item.id == "a" }.reason)
    }

    @Test
    fun `greedy selection respects budget exactly`() {
        // Sizes 5h, 3h, 4h; equal ROI (constant valuator), equal priority, distinct createdAt for determinism.
        val five = item("five", minutes = 5 * 60, createdAt = 1)
        val three = item("three", minutes = 3 * 60, createdAt = 2)
        val four = item("four", minutes = 4 * 60, createdAt = 3)
        // Valuator scales dollars with hours so ROI equals the same per-hour rate for all.
        val plan = service.plan(listOf(five, three, four), opts(8.0, valuator = { it.estimatedFixMinutes / 60.0 * 10.0 }))
        val ids = plan.selected.map { it.item.id }
        // With identical ROI, tie-break goes to priority (same), then oldest (createdAt asc): five, three, four.
        // Greedy picks five (5), then three (5+3=8, fits), then four rejected (8+4>8).
        assertEquals(listOf("five", "three"), ids)
        assertEquals(8.0, plan.totalHours)
        val declined = plan.declined.single()
        assertEquals("four", declined.item.id)
        assertEquals(DeclineReason.BUDGET, declined.reason)
    }

    @Test
    fun `ROI beats priority in sort`() {
        // Low-priority high-ROI beats high-priority low-ROI.
        val lowHighRoi = item("lhr", priority = DebtPriority.LOW, minutes = 60) // 1h
        val highLowRoi = item("hlr", priority = DebtPriority.CRITICAL, minutes = 60) // 1h
        val valuator: (TechDebtItem) -> Double = { if (it.id == "lhr") 100.0 else 5.0 }
        val plan = service.plan(listOf(highLowRoi, lowHighRoi), opts(1.0, valuator = valuator))
        assertEquals(listOf("lhr"), plan.selected.map { it.item.id })
    }

    @Test
    fun `priority breaks ROI ties`() {
        val low = item("low", priority = DebtPriority.LOW, createdAt = 1)
        val high = item("high", priority = DebtPriority.HIGH, createdAt = 2)
        val plan = service.plan(listOf(low, high), opts(1.0, valuator = { 10.0 }))
        // Same ROI (both 10 dollars / 1h); higher priority first. Budget fits only one.
        assertEquals(listOf("high"), plan.selected.map { it.item.id })
    }

    @Test
    fun `age breaks priority ties`() {
        val newer = item("newer", priority = DebtPriority.HIGH, createdAt = 500)
        val older = item("older", priority = DebtPriority.HIGH, createdAt = 100)
        val plan = service.plan(listOf(newer, older), opts(1.0, valuator = { 10.0 }))
        // Same ROI, same priority; older (smaller createdAt) first.
        assertEquals(listOf("older"), plan.selected.map { it.item.id })
    }

    @Test
    fun `overflow-once accepts a single oversize item within 20 percent`() {
        // Budget 10h, one item 11h => 10% overflow, <=12h cap, accepted.
        val oversize = item("big", minutes = 11 * 60)
        val plan = service.plan(
            listOf(oversize),
            opts(10.0, overflow = true, valuator = { 100.0 })
        )
        assertEquals(listOf("big"), plan.selected.map { it.item.id })
        assertEquals(11.0, plan.totalHours)
    }

    @Test
    fun `overflow rejects item more than 20 percent over budget`() {
        // Budget 10h, item 13h => >20% over, rejected even with overflow flag.
        val tooBig = item("huge", minutes = 13 * 60)
        val plan = service.plan(
            listOf(tooBig),
            opts(10.0, overflow = true, valuator = { 100.0 })
        )
        assertTrue(plan.selected.isEmpty())
        val d = plan.declined.single()
        assertEquals("huge", d.item.id)
        assertEquals(DeclineReason.BUDGET, d.reason)
    }

    @Test
    fun `overflow disabled leaves oversize declined`() {
        val oversize = item("big", minutes = 11 * 60)
        val plan = service.plan(listOf(oversize), opts(10.0, overflow = false, valuator = { 100.0 }))
        assertTrue(plan.selected.isEmpty())
        assertEquals(DeclineReason.BUDGET, plan.declined.single().reason)
    }

    @Test
    fun `totals math matches selected`() {
        val a = item("a", minutes = 120) // 2h
        val b = item("b", minutes = 60)  // 1h
        val plan = service.plan(listOf(a, b), opts(8.0, valuator = { if (it.id == "a") 50.0 else 20.0 }))
        assertEquals(2, plan.itemCount)
        assertEquals(3.0, plan.totalHours)
        assertEquals(70.0, plan.totalMonthlyDollarSavings)
    }

    @Test
    fun `markdown escapes pipes brackets and backticks in titles and paths`() {
        val nasty = item(
            id = "nasty",
            description = "Fix | `boom` [critical]",
            filePath = "src/[legacy]/old|file.kt"
        )
        val plan = service.plan(listOf(nasty), opts(8.0, valuator = { 5.0 }))
        val md = plan.toMarkdown()
        assertTrue(md.contains("Fix \\| \\`boom\\` \\[critical\\]"), "Title not escaped: $md")
        assertTrue(md.contains("src/\\[legacy\\]/old\\|file.kt"), "Path not escaped: $md")
        // Ensure no raw unescaped pipe from the title leaked inside a row (would break table parsing).
        val selectedRow = md.lines().first { it.contains("nasty") }
        val totalPipes = selectedRow.count { it == '|' }
        val escapedPipes = Regex("""\\\|""").findAll(selectedRow).count()
        // 8 structural pipes (7 columns) + 2 escaped pipes (1 in title, 1 in path) = 10 total.
        assertEquals(8, totalPipes - escapedPipes)
    }

    @Test
    fun `markdown contains summary headings`() {
        val plan = service.plan(listOf(item("x")), opts(4.0, valuator = { 10.0 }))
        val md = plan.toMarkdown()
        assertTrue(md.contains("# Sprint Planning Recommendation"))
        assertTrue(md.contains("## Summary"))
        assertTrue(md.contains("## Selected Items"))
    }

    @Test
    fun `declined collapsed section only rendered when non-empty`() {
        val only = item("only", minutes = 60)
        val plan = service.plan(listOf(only), opts(10.0, valuator = { 5.0 }))
        assertFalse(plan.toMarkdown().contains("<details>"))
    }

    @Test
    fun `valuator returning negative or NaN treated as zero`() {
        val a = item("a", minutes = 60)
        val plan = service.plan(listOf(a), opts(8.0, valuator = { Double.NaN }))
        assertEquals(0.0, plan.selected.single().monthlyInterestSavedDollars)
        assertEquals(0.0, plan.selected.single().roi)
    }

    @Test
    fun `declined items carry computed hours and savings`() {
        val excluded = item("x", tags = setOf("skip"), minutes = 90)
        val plan = service.plan(listOf(excluded), opts(8.0, exclude = setOf("skip"), valuator = { 9.0 }))
        val d = plan.declined.single()
        assertEquals(1.5, d.fixEffortHours)
        assertEquals(9.0, d.monthlyInterestSavedDollars)
        assertEquals(6.0, d.roi)
        assertEquals(DeclineReason.FILTERED, d.reason)
    }
}
