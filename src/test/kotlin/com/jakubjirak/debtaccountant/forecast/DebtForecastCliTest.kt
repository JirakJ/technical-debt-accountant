package com.jakubjirak.debtaccountant.forecast

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.digest.DigestSnapshot
import com.jakubjirak.debtaccountant.digest.TechDebtItemSnapshot
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class DebtForecastCliTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun writeTrend(dir: Path, counts: List<Int>) {
        val start = Instant.parse("2026-01-05T00:00:00Z").toEpochMilli()
        val week = 7L * 24 * 60 * 60 * 1000
        counts.forEachIndexed { i, c ->
            val items = (0 until c).map { k ->
                TechDebtItemSnapshot(
                    id = "id-$i-$k",
                    filePath = "f$k.kt",
                    category = DebtCategory.CODE_SMELL,
                    priority = DebtPriority.HIGH,
                    description = "d",
                    estimatedFixMinutes = 30,
                    totalWorkaroundMinutes = 0,
                    createdAt = 0L, updatedAt = 0L, resolvedAt = null, resolved = false,
                    author = "a", tags = emptyList(), dollarValue = 50.0, slaStatus = "OK"
                )
            }
            val snap = DigestSnapshot(start + i * week, "w$i", items)
            Files.writeString(dir.resolve("w$i.json"), gson.toJson(snap))
        }
    }

    @Test
    fun `help exits 0`() {
        val r = DebtForecastCli.run(arrayOf("--help"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("Usage"))
    }

    @Test
    fun `missing snapshots-dir fails`() {
        val r = DebtForecastCli.run(arrayOf("--out=x"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("--snapshots-dir"))
    }

    @Test
    fun `missing out fails`(@TempDir tmp: Path) {
        val r = DebtForecastCli.run(arrayOf("--snapshots-dir=$tmp"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("--out"))
    }

    @Test
    fun `invalid format fails`(@TempDir tmp: Path) {
        val r = DebtForecastCli.run(arrayOf("--snapshots-dir=$tmp",
            "--out=${tmp.resolve("o")}", "--format=xml"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `invalid horizon fails`(@TempDir tmp: Path) {
        val r = DebtForecastCli.run(arrayOf("--snapshots-dir=$tmp",
            "--out=${tmp.resolve("o")}", "--horizon-weeks=zero"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `nonexistent dir fails`(@TempDir tmp: Path) {
        val missing = tmp.resolve("nope")
        val r = DebtForecastCli.run(arrayOf("--snapshots-dir=$missing",
            "--out=${tmp.resolve("o")}"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `happy path markdown`(@TempDir tmp: Path) {
        val snaps = tmp.resolve("snaps").also { Files.createDirectories(it) }
        writeTrend(snaps, listOf(30, 27, 24, 21, 18, 15))
        val out = tmp.resolve("f.md")
        val r = DebtForecastCli.run(arrayOf(
            "--snapshots-dir=$snaps",
            "--out=$out",
            "--horizon-weeks=4",
            "--model=linear",
            "--format=md",
            "--fail-on-degrading"
        ))
        assertEquals(0, r.exitCode, r.stderr)
        assertTrue(Files.exists(out))
        val body = Files.readString(out)
        assertTrue(body.contains("Debt Trend Forecast"))
    }

    @Test
    fun `fail-on-degrading exits 1 on degrading trend`(@TempDir tmp: Path) {
        val snaps = tmp.resolve("snaps").also { Files.createDirectories(it) }
        writeTrend(snaps, listOf(10, 13, 16, 19, 22, 25))
        val out = tmp.resolve("f.md")
        val r = DebtForecastCli.run(arrayOf(
            "--snapshots-dir=$snaps",
            "--out=$out",
            "--model=linear",
            "--fail-on-degrading"
        ))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("DEGRADING"))
        // but output was still written
        assertTrue(Files.exists(out))
    }

    @Test
    fun `json format works`(@TempDir tmp: Path) {
        val snaps = tmp.resolve("s").also { Files.createDirectories(it) }
        writeTrend(snaps, listOf(5, 6, 7, 8, 9))
        val out = tmp.resolve("f.json")
        val r = DebtForecastCli.run(arrayOf(
            "--snapshots-dir=$snaps", "--out=$out", "--format=json"
        ))
        assertEquals(0, r.exitCode)
        val body = Files.readString(out)
        assertTrue(body.trimStart().startsWith("{"))
    }

    @Test
    fun `html format works`(@TempDir tmp: Path) {
        val snaps = tmp.resolve("s").also { Files.createDirectories(it) }
        writeTrend(snaps, listOf(5, 6, 7, 8, 9))
        val out = tmp.resolve("f.html")
        val r = DebtForecastCli.run(arrayOf(
            "--snapshots-dir=$snaps", "--out=$out", "--format=html"
        ))
        assertEquals(0, r.exitCode)
        val body = Files.readString(out)
        assertTrue(body.contains("<svg"))
    }

    @Test
    fun `unknown argument fails`() {
        val r = DebtForecastCli.run(arrayOf("--bogus=1"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `invalid model fails`(@TempDir tmp: Path) {
        val r = DebtForecastCli.run(arrayOf("--snapshots-dir=$tmp",
            "--out=${tmp.resolve("o")}", "--model=cubic"))
        assertEquals(1, r.exitCode)
    }
}
