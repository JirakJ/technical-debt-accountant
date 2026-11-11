package com.jakubjirak.debtaccountant.statusbar

import com.jakubjirak.debtaccountant.health.DebtGrade
import com.jakubjirak.debtaccountant.health.DebtHealth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusBarTextBuilderTest {

    @Suppress("LongParameterList") // test fixture builder: every parameter has a default
    private fun health(
        grade: DebtGrade = DebtGrade.B,
        score: Int = 80,
        activeItems: Int = 3,
        monthlyInterest: Double = 0.0,
        budgetDollars: Double = 0.0,
        overBudget: Boolean = false
    ) = DebtHealth(
        score = score,
        grade = grade,
        activeItems = activeItems,
        weightedSeverity = 6.0,
        monthlyInterest = monthlyInterest,
        budgetDollars = budgetDollars,
        overBudget = overBudget
    )

    @Test
    fun `widget text shows only the grade when there is no interest`() {
        assertEquals("Debt B", StatusBarTextBuilder.widgetText(health(), "$0.00"))
    }

    @Test
    fun `widget text appends monthly interest when positive`() {
        val text = StatusBarTextBuilder.widgetText(health(monthlyInterest = 1234.0), "$1,234.00")
        assertEquals("Debt B · $1,234.00/mo", text)
    }

    @Test
    fun `tooltip always reports grade, score and item count`() {
        val tooltip = StatusBarTextBuilder.tooltip(health(score = 80, activeItems = 3), "$0.00", "$0.00")
        assertTrue(tooltip.contains("B (Good)"))
        assertTrue(tooltip.contains("Score: 80/100"))
        assertTrue(tooltip.contains("Active items: 3"))
        assertTrue(tooltip.contains("Click to open"))
    }

    @Test
    fun `tooltip omits budget line when no budget is set`() {
        val tooltip = StatusBarTextBuilder.tooltip(health(budgetDollars = 0.0), "$0.00", "$0.00")
        assertTrue(!tooltip.contains("Budget:"))
    }

    @Test
    fun `tooltip flags over budget`() {
        val tooltip = StatusBarTextBuilder.tooltip(
            health(monthlyInterest = 500.0, budgetDollars = 100.0, overBudget = true),
            "$500.00",
            "$100.00"
        )
        assertTrue(tooltip.contains("OVER BUDGET"))
        assertTrue(tooltip.contains("Monthly interest: $500.00"))
    }

    @Test
    fun `tooltip shows within budget when under threshold`() {
        val tooltip = StatusBarTextBuilder.tooltip(
            health(monthlyInterest = 50.0, budgetDollars = 100.0, overBudget = false),
            "$50.00",
            "$100.00"
        )
        assertTrue(tooltip.contains("within budget"))
    }
}
