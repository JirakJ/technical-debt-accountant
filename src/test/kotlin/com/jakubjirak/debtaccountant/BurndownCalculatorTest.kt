package com.jakubjirak.debtaccountant

import com.jakubjirak.debtaccountant.burndown.BurndownCalculator
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtTrend
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BurndownCalculatorTest {

    private val calculator = BurndownCalculator()

    @Test
    fun `calculate burndown returns correct number of points`() {
        val items = listOf(createItem())
        val points = calculator.calculateBurndown(items, 7)
        assertEquals(8, points.size)
    }

    @Test
    fun `detect trend returns stable for empty list`() {
        val trend = calculator.detectTrend(emptyList())
        assertEquals(DebtTrend.STABLE, trend)
    }

    @Test
    fun `export csv has header`() {
        val points = calculator.calculateBurndown(listOf(createItem()), 3)
        val csv = calculator.exportCsv(points)
        assertTrue(csv.startsWith("Date,New Items,Resolved Items,Cumulative Active"))
    }

    @Test
    fun `export csv has correct number of rows`() {
        val points = calculator.calculateBurndown(listOf(createItem()), 3)
        val csv = calculator.exportCsv(points)
        val lines = csv.trim().lines()
        assertEquals(5, lines.size)
    }

    private fun createItem() = TechDebtItem(
        filePath = "test.kt",
        category = DebtCategory.CODE_SMELL,
        description = "Test"
    )
}
