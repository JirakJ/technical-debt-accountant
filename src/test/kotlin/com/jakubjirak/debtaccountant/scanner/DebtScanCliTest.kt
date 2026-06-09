package com.jakubjirak.debtaccountant.scanner

import com.google.gson.Gson
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DebtScanCliTest {

    private fun writeSource(dir: Path) {
        Files.writeString(dir.resolve("App.kt"), "// TODO: wire DI\n// FIXME: leaking handle\n")
    }

    @Test
    fun `happy path renders markdown table`(@TempDir dir: Path) {
        writeSource(dir)
        val result = DebtScanCli.run(arrayOf("--root=$dir"))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Found **2** debt comment(s)."))
        assertTrue(result.stdout.contains("| TODO | `App.kt:1` | wire DI |"))
        assertTrue(result.stdout.contains("| FIXME | `App.kt:2` | leaking handle |"))
        assertTrue(result.stdout.contains("- FIXME: 1"))
    }

    @Test
    fun `json format includes mapping fields`(@TempDir dir: Path) {
        writeSource(dir)
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--format=json"))
        assertEquals(0, result.exitCode)
        val parsed = Gson().fromJson(result.stdout, Map::class.java)
        assertEquals(2.0, parsed["total"])
        val comments = parsed["comments"] as List<*>
        val first = comments[0] as Map<*, *>
        assertEquals("TODO", first["marker"])
        assertEquals("CODE_SMELL", first["category"])
        assertEquals("MEDIUM", first["priority"])
    }

    @Test
    fun `inventory deduplicates known comments`(@TempDir dir: Path) {
        writeSource(dir)
        val inventory = listOf(
            TechDebtItem(
                filePath = "App.kt", lineStart = 1, lineEnd = 1,
                category = DebtCategory.CODE_SMELL, description = "TODO: wire DI",
                tags = mutableSetOf(DebtCommentScanner.SCAN_TAG)
            )
        )
        val inventoryFile = dir.resolve("inventory.json")
        Files.writeString(inventoryFile, Gson().toJson(inventory))

        val result = DebtScanCli.run(arrayOf("--root=$dir", "--inventory=$inventoryFile"))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Found **1** debt comment(s)."))
        assertTrue(result.stdout.contains("FIXME"))
    }

    @Test
    fun `fail-over exits 2 when threshold exceeded`(@TempDir dir: Path) {
        writeSource(dir)
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--fail-over=1"))
        assertEquals(2, result.exitCode)
    }

    @Test
    fun `fail-over passes when within threshold`(@TempDir dir: Path) {
        writeSource(dir)
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--fail-over=2"))
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `out writes report file`(@TempDir dir: Path) {
        writeSource(dir)
        val out = dir.resolve("reports/scan.md")
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--out=$out"))
        assertEquals(0, result.exitCode)
        assertTrue(Files.readString(out).contains("Found **2** debt comment(s)."))
    }

    @Test
    fun `missing root is an error`() {
        val result = DebtScanCli.run(arrayOf("--format=md"))
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("--root"))
    }

    @Test
    fun `unknown argument is an error`(@TempDir dir: Path) {
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--bogus"))
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("Unknown argument"))
    }

    @Test
    fun `invalid format is an error`(@TempDir dir: Path) {
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--format=xml"))
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("--format"))
    }

    @Test
    fun `non-integer fail-over is an error`(@TempDir dir: Path) {
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--fail-over=abc"))
        assertEquals(1, result.exitCode)
    }

    @Test
    fun `nonexistent root is an error`(@TempDir dir: Path) {
        val result = DebtScanCli.run(arrayOf("--root=${dir.resolve("missing")}"))
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("Not a directory"))
    }

    @Test
    fun `help exits 0`() {
        val result = DebtScanCli.run(arrayOf("--help"))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Usage"))
    }

    @Test
    fun `unreadable inventory is an error`(@TempDir dir: Path) {
        writeSource(dir)
        val bad = dir.resolve("bad.json")
        Files.writeString(bad, "{not json")
        val result = DebtScanCli.run(arrayOf("--root=$dir", "--inventory=$bad"))
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("Failed to read inventory"))
    }
}
