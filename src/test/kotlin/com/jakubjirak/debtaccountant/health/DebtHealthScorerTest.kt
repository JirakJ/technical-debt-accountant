package com.jakubjirak.debtaccountant.health

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebtHealthScorerTest {

    private fun item(
        priority: DebtPriority = DebtPriority.MEDIUM,
        resolved: Boolean = false
    ) = TechDebtItem(
        filePath = "src/Foo.kt",
        category = DebtCategory.CODE_SMELL,
        description = "debt",
        priority = priority,
        resolved = resolved
    )

    @Test
    fun `empty inventory is a perfect A`() {
        val health = DebtHealthScorer.score(emptyList())
        assertEquals(100, health.score)
        assertEquals(DebtGrade.A, health.grade)
        assertEquals(0, health.activeItems)
        assertEquals(0.0, health.weightedSeverity)
    }

    @Test
    fun `single critical item costs only a few points`() {
        val health = DebtHealthScorer.score(listOf(item(DebtPriority.CRITICAL)))
        assertEquals(4.0, health.weightedSeverity)
        // 100 * exp(-4/25) ≈ 85.2
        assertEquals(85, health.score)
        assertEquals(DebtGrade.B, health.grade)
    }

    @Test
    fun `resolved items are ignored`() {
        val health = DebtHealthScorer.score(
            listOf(
                item(DebtPriority.CRITICAL, resolved = true),
                item(DebtPriority.CRITICAL, resolved = true)
            )
        )
        assertEquals(100, health.score)
        assertEquals(0, health.activeItems)
        assertEquals(0.0, health.weightedSeverity)
    }

    @Test
    fun `more debt always lowers the score`() {
        val few = DebtHealthScorer.score(List(2) { item(DebtPriority.MEDIUM) }).score
        val many = DebtHealthScorer.score(List(20) { item(DebtPriority.MEDIUM) }).score
        assertTrue(many < few, "expected $many < $few")
    }

    @Test
    fun `score never drops below zero for huge inventories`() {
        val health = DebtHealthScorer.score(List(500) { item(DebtPriority.CRITICAL) })
        assertEquals(0, health.score)
        assertEquals(DebtGrade.F, health.grade)
    }

    @Test
    fun `higher priority weighs more than lower priority`() {
        val critical = DebtHealthScorer.score(List(3) { item(DebtPriority.CRITICAL) }).score
        val low = DebtHealthScorer.score(List(3) { item(DebtPriority.LOW) }).score
        assertTrue(critical < low, "expected $critical < $low")
    }

    @Test
    fun `budget flag trips only when interest exceeds a positive budget`() {
        val items = listOf(item(DebtPriority.HIGH))
        assertTrue(DebtHealthScorer.score(items, monthlyInterest = 250.0, budgetDollars = 100.0).overBudget)
        assertFalse(DebtHealthScorer.score(items, monthlyInterest = 50.0, budgetDollars = 100.0).overBudget)
    }

    @Test
    fun `zero budget disables the over-budget flag`() {
        val items = listOf(item(DebtPriority.HIGH))
        val health = DebtHealthScorer.score(items, monthlyInterest = 9_999.0, budgetDollars = 0.0)
        assertFalse(health.overBudget)
        assertEquals(9_999.0, health.monthlyInterest)
    }
}
