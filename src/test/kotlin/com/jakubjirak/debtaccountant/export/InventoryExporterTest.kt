package com.jakubjirak.debtaccountant.export

import com.google.gson.Gson
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryExporterTest {

    private fun item(
        description: String = "TODO: extract constant",
        workaroundMinutes: Int = 0,
        resolved: Boolean = false,
    ) = TechDebtItem(
        id = "item-1",
        filePath = "src/App.kt",
        lineStart = 10,
        lineEnd = 12,
        category = DebtCategory.CODE_SMELL,
        priority = DebtPriority.HIGH,
        description = description,
        estimatedFixMinutes = 90,
        workaroundLogs = if (workaroundMinutes > 0) {
            mutableListOf(WorkaroundLog(durationMinutes = workaroundMinutes))
        } else {
            mutableListOf()
        },
        createdAt = 1_700_000_000_000L,
        author = "jakub",
        tags = mutableSetOf("scanned", "todo"),
        resolved = resolved,
        resolvedAt = if (resolved) 1_700_100_000_000L else null,
    )

    // --- CSV -------------------------------------------------------------------

    @Test
    fun `csv starts with header row`() {
        val csv = InventoryExporter.toCsv(emptyList(), 80.0, "USD")
        assertTrue(csv.startsWith("id,filePath,lineStart,lineEnd,category,priority,description,"))
        assertEquals(1, csv.trim().lines().size)
    }

    @Test
    fun `csv renders one row per item with computed costs`() {
        val csv = InventoryExporter.toCsv(listOf(item(workaroundMinutes = 120)), 60.0, "USD")
        val lines = csv.trim().split("\r\n")
        assertEquals(2, lines.size)
        val row = lines[1]
        assertTrue(row.startsWith("item-1,src/App.kt,10,12,CODE_SMELL,HIGH,"))
        // 120 min workaround @ 60/h = 120.00; 90 min fix @ 60/h = 90.00
        assertTrue(row.contains(",120,120.00,90.00,USD"))
    }

    @Test
    fun `csv escapes commas and quotes per rfc4180`() {
        val tricky = item(description = """Says "fix me", really""")
        val csv = InventoryExporter.toCsv(listOf(tricky), 80.0, "USD")
        assertTrue(csv.contains("\"Says \"\"fix me\"\", really\""))
    }

    @Test
    fun `csv escapes newlines in fields`() {
        val multiline = item(description = "line one\nline two")
        val csv = InventoryExporter.toCsv(listOf(multiline), 80.0, "USD")
        assertTrue(csv.contains("\"line one\nline two\""))
    }

    @Test
    fun `csv formats timestamps as iso instant`() {
        val csv = InventoryExporter.toCsv(listOf(item()), 80.0, "USD")
        assertTrue(csv.contains("2023-11-14T22:13:20Z"))
    }

    @Test
    fun `csv leaves resolvedAt empty for active items`() {
        val csv = InventoryExporter.toCsv(listOf(item()), 80.0, "USD")
        assertTrue(csv.contains(",false,,90,"))
    }

    @Test
    fun `csv fills resolvedAt for resolved items`() {
        val csv = InventoryExporter.toCsv(listOf(item(resolved = true)), 80.0, "USD")
        assertTrue(csv.contains(",true,2023-11-16T02:00:00Z,"))
    }

    @Test
    fun `csv joins tags sorted with semicolon`() {
        val csv = InventoryExporter.toCsv(listOf(item()), 80.0, "USD")
        assertTrue(csv.contains(",scanned;todo,"))
    }

    @Test
    fun `escapeCsv leaves plain fields untouched`() {
        assertEquals("plain", InventoryExporter.escapeCsv("plain"))
    }

    @Test
    fun `escapeCsv quotes field with comma`() {
        assertEquals("\"a,b\"", InventoryExporter.escapeCsv("a,b"))
    }

    @Test
    fun `escapeCsv doubles quotes`() {
        assertEquals("\"a\"\"b\"", InventoryExporter.escapeCsv("a\"b"))
    }

    // --- JSON ------------------------------------------------------------------

    @Test
    fun `json contains summary with totals`() {
        val items = listOf(item(workaroundMinutes = 60), item(resolved = true))
        val json = InventoryExporter.toJson(items, 60.0, "EUR")
        val parsed = Gson().fromJson(json, Map::class.java)
        val summary = parsed["summary"] as Map<*, *>
        assertEquals(2.0, summary["totalItems"])
        assertEquals(1.0, summary["activeItems"])
        assertEquals(1.0, summary["resolvedItems"])
        assertEquals(60.0, summary["totalWorkaroundMinutes"])
        assertEquals(60.0, summary["totalWorkaroundCost"])
        assertEquals(180.0, summary["totalEstimatedFixCost"]) // 2 × 90 min @ 60/h
        assertEquals("EUR", summary["currency"])
    }

    @Test
    fun `json items carry computed costs and iso dates`() {
        val json = InventoryExporter.toJson(listOf(item(workaroundMinutes = 30)), 80.0, "USD")
        val parsed = Gson().fromJson(json, Map::class.java)
        val items = parsed["items"] as List<*>
        val first = items[0] as Map<*, *>
        assertEquals("item-1", first["id"])
        assertEquals(40.0, first["workaroundCost"]) // 30 min @ 80/h
        assertEquals(120.0, first["estimatedFixCost"]) // 90 min @ 80/h
        assertEquals("2023-11-14T22:13:20Z", first["createdAt"])
        assertEquals(false, first["resolved"])
    }

    @Test
    fun `json for empty inventory has zeroed summary and empty items`() {
        val json = InventoryExporter.toJson(emptyList(), 80.0, "USD")
        val parsed = Gson().fromJson(json, Map::class.java)
        val summary = parsed["summary"] as Map<*, *>
        assertEquals(0.0, summary["totalItems"])
        assertTrue((parsed["items"] as List<*>).isEmpty())
    }

    @Test
    fun `json omits no field on resolved item`() {
        val json = InventoryExporter.toJson(listOf(item(resolved = true)), 80.0, "USD")
        assertTrue(json.contains("\"resolvedAt\": \"2023-11-16T02:00:00Z\""))
        assertFalse(json.contains("\"resolved\": false"))
    }
}
