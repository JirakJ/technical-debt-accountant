package com.jakubjirak.debtaccountant

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DollarValuatorTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    // 60/h -> exactly 1 currency unit per minute keeps the arithmetic obvious.
    private val valuator = DollarValuator(
        hourlyRateProvider = { 60.0 },
        currencyProvider = { "EUR" },
        clock = { now },
    )

    private fun item(
        id: String = "i",
        fixMinutes: Int = 120,
        logs: List<WorkaroundLog> = emptyList(),
    ) = TechDebtItem(
        id = id,
        filePath = "src/A.kt",
        category = DebtCategory.entries.first(),
        description = "d",
        estimatedFixMinutes = fixMinutes,
        workaroundLogs = logs.toMutableList(),
        createdAt = now,
        updatedAt = now,
    )

    private fun log(minutes: Int, daysAgo: Long) =
        WorkaroundLog(id = "l$minutes-$daysAgo", timestamp = now - daysAgo * day, durationMinutes = minutes)

    @Test
    fun `format currency USD`() {
        val formatted = DollarValuator().formatCurrency(100.0, "USD")
        assertTrue(formatted.contains("100"))
    }

    @Test
    fun `format currency EUR`() {
        val formatted = DollarValuator().formatCurrency(50.5, "EUR")
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun `format currency falls back to plain dollars for unknown code`() {
        assertEquals("$25.00", DollarValuator().formatCurrency(25.0, "XYZ"))
    }

    @Test
    fun `valuate computes workaround, fix cost and currency`() {
        val v = valuator.valuate(item(fixMinutes = 90, logs = listOf(log(10, 1), log(20, 100))))
        assertEquals(30.0, v.totalWorkaroundCost, 1e-9)
        assertEquals(90.0, v.estimatedFixCost, 1e-9)
        assertEquals("EUR", v.currency)
        assertEquals("i", v.itemId)
    }

    @Test
    fun `monthly interest only counts logs from the last 30 days`() {
        val v = valuator.valuate(item(logs = listOf(log(10, 0), log(5, 29), log(100, 31))))
        assertEquals(15.0, v.monthlyInterest, 1e-9)
    }

    @Test
    fun `roi months is fix cost divided by monthly interest`() {
        val v = valuator.valuate(item(fixMinutes = 120, logs = listOf(log(30, 2))))
        assertEquals(4.0, v.roiMonths, 1e-9)
    }

    @Test
    fun `roi months is max value when there is no recurring interest`() {
        val v = valuator.valuate(item(logs = listOf(log(30, 60))))
        assertEquals(0.0, v.monthlyInterest, 1e-9)
        assertEquals(Double.MAX_VALUE, v.roiMonths)
    }

    @Test
    fun `totals aggregate across items`() {
        val items = listOf(
            item(id = "a", fixMinutes = 60, logs = listOf(log(10, 1))),
            item(id = "b", fixMinutes = 30, logs = listOf(log(5, 40))),
        )
        assertEquals(15.0, valuator.totalWorkaroundCost(items), 1e-9)
        assertEquals(90.0, valuator.totalFixCost(items), 1e-9)
        assertEquals(10.0, valuator.totalMonthlyInterest(items), 1e-9)
        assertEquals(2, valuator.valuateAll(items).size)
    }

    @Test
    fun `totals of empty list are zero`() {
        assertEquals(0.0, valuator.totalWorkaroundCost(emptyList()))
        assertEquals(0.0, valuator.totalFixCost(emptyList()))
        assertEquals(0.0, valuator.totalMonthlyInterest(emptyList()))
    }

    @Test
    fun `hourly rate scales costs linearly`() {
        val expensive = DollarValuator(hourlyRateProvider = { 120.0 }, currencyProvider = { "USD" }, clock = { now })
        assertEquals(240.0, expensive.valuate(item(fixMinutes = 120)).estimatedFixCost, 1e-9)
    }
}
