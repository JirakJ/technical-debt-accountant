package com.jakubjirak.debtaccountant

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TechDebtItemTest {

    @Test
    fun `total workaround minutes sums all logs`() {
        val item = createItem().copy(
            workaroundLogs = mutableListOf(
                WorkaroundLog(durationMinutes = 10, note = "test1"),
                WorkaroundLog(durationMinutes = 20, note = "test2"),
                WorkaroundLog(durationMinutes = 5, note = "test3")
            )
        )
        assertEquals(35, item.totalWorkaroundMinutes)
    }

    @Test
    fun `isResolved returns true when resolved flag set`() {
        val item = createItem().copy(resolved = true)
        assertTrue(item.isResolved)
    }

    @Test
    fun `isResolved returns true when resolvedAt is set`() {
        val item = createItem().copy(resolvedAt = System.currentTimeMillis())
        assertTrue(item.isResolved)
    }

    @Test
    fun `isResolved returns false for active item`() {
        val item = createItem()
        assertFalse(item.isResolved)
    }

    @Test
    fun `item has correct default values`() {
        val item = createItem()
        assertEquals(DebtPriority.MEDIUM, item.priority)
        assertEquals(60, item.estimatedFixMinutes)
        assertEquals(0, item.totalWorkaroundMinutes)
        assertFalse(item.isResolved)
    }

    private fun createItem() = TechDebtItem(
        filePath = "src/main/Example.kt",
        category = DebtCategory.CODE_SMELL,
        description = "Test debt item"
    )
}
