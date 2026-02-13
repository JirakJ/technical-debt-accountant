package com.jakubjirak.debtaccountant

import com.jakubjirak.debtaccountant.hotspot.HotspotDetector
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotspotDetectorTest {

    private val detector = HotspotDetector()

    @Test
    fun `detect hotspots groups by file`() {
        val items = listOf(
            createItem("file1.kt", DebtPriority.HIGH),
            createItem("file1.kt", DebtPriority.MEDIUM),
            createItem("file2.kt", DebtPriority.LOW)
        )

        val hotspots = detector.detectHotspots(items)
        assertEquals(2, hotspots.size)
        assertEquals("file1.kt", hotspots[0].filePath)
        assertEquals(2, hotspots[0].debtCount)
    }

    @Test
    fun `detect hotspots excludes resolved items`() {
        val items = listOf(
            createItem("file1.kt", DebtPriority.HIGH),
            createItem("file1.kt", DebtPriority.HIGH).copy(resolved = true)
        )

        val hotspots = detector.detectHotspots(items)
        assertEquals(1, hotspots.size)
        assertEquals(1, hotspots[0].debtCount)
    }

    @Test
    fun `detect hotspots respects limit`() {
        val items = (1..20).map { createItem("file$it.kt", DebtPriority.MEDIUM) }
        val hotspots = detector.detectHotspots(items, 5)
        assertEquals(5, hotspots.size)
    }

    @Test
    fun `hotspot score accounts for priority weight`() {
        val highItem = createItem("high.kt", DebtPriority.HIGH)
        val lowItem = createItem("low.kt", DebtPriority.LOW)

        val hotspots = detector.detectHotspots(listOf(highItem, lowItem))
        assertTrue(hotspots[0].score > hotspots[1].score)
    }

    private fun createItem(file: String, priority: DebtPriority): TechDebtItem {
        return TechDebtItem(
            filePath = file,
            category = DebtCategory.CODE_SMELL,
            priority = priority,
            description = "Test item in $file",
            workaroundLogs = mutableListOf(WorkaroundLog(durationMinutes = 10))
        )
    }
}
