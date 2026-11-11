package com.jakubjirak.debtaccountant.sla

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class SlaCliTest {

    private val now = Instant.parse("2026-04-17T12:00:00Z")
    private val gson = GsonBuilder().create()

    private fun writeInventory(dir: Path, items: List<TechDebtItem>): Path {
        val file = dir.resolve("inventory.json")
        Files.writeString(file, gson.toJson(items))
        return file
    }

    private fun mkItem(id: String, ageDays: Long, priority: DebtPriority = DebtPriority.HIGH): TechDebtItem =
        TechDebtItem(
            id = id,
            filePath = "src/$id.kt",
            category = DebtCategory.values().first(),
            priority = priority,
            description = "desc-$id",
            createdAt = now.minusSeconds(ageDays * 86_400).toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )

    @Test
    fun `missing --inventory exits 1`() {
        val r = SlaCli.run(emptyArray(), now)
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Missing"))
    }

    @Test
    fun `unknown flag exits 1`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, emptyList())
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--nope"), now)
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Unknown argument"))
    }

    @Test
    fun `invalid --output exits 1`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, emptyList())
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--output=xml"), now)
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `clean inventory exits 0`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 1)))
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--fail-on-breach"), now)
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("🟢"))
    }

    @Test
    fun `--fail-on-breach exits 2 when breached`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 60)))
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--fail-on-breach"), now)
        assertEquals(2, r.exitCode)
    }

    @Test
    fun `without --fail-on-breach breach still exits 0`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 60)))
        val r = SlaCli.run(arrayOf("--inventory=$inv"), now)
        assertEquals(0, r.exitCode)
    }

    @Test
    fun `--output=json emits JSON`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 60)))
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--output=json"), now)
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.trimStart().startsWith("{"))
        assertTrue(r.stdout.contains("\"breached\""))
    }

    @Test
    fun `--output=md is default and contains banner`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 60)))
        val r = SlaCli.run(arrayOf("--inventory=$inv"), now)
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("# Debt SLA Report"))
    }

    @Test
    fun `missing inventory file exits 1`(@TempDir tmp: Path) {
        val r = SlaCli.run(arrayOf("--inventory=${tmp.resolve("nope.json")}"), now)
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Failed to read inventory"))
    }

    @Test
    fun `missing policy file exits 1`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, emptyList())
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--policy=${tmp.resolve("no.json")}"), now)
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Policy file not found"))
    }

    @Test
    fun `invalid policy exits 1`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, emptyList())
        val badPolicy = tmp.resolve("bad.json")
        Files.writeString(
            badPolicy,
            """{"rules":[{"priority":"HIGH","maxAgeDays":0,"warnAtPercent":80}],""" +
                """"defaultRule":{"priority":"LOW","maxAgeDays":365,"warnAtPercent":90}}"""
        )
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--policy=$badPolicy"), now)
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Invalid policy"))
    }

    @Test
    fun `custom policy file is used`(@TempDir tmp: Path) {
        val inv = writeInventory(tmp, listOf(mkItem("a", ageDays = 20)))
        val strictPolicy = tmp.resolve("strict.json")
        Files.writeString(
            strictPolicy,
            """{"rules":[{"priority":"HIGH","maxAgeDays":10,"warnAtPercent":80,"escalateOnBreach":false,"escalatedPriority":null}],""" +
                """"defaultRule":{"priority":"LOW","maxAgeDays":10,"warnAtPercent":80,"escalateOnBreach":false,"escalatedPriority":null}}"""
        )
        val r = SlaCli.run(arrayOf("--inventory=$inv", "--policy=$strictPolicy", "--fail-on-breach"), now)
        assertEquals(2, r.exitCode)
    }

    @Test
    fun `help prints usage and exits 0`() {
        val r = SlaCli.run(arrayOf("--help"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("Usage"))
    }
}
