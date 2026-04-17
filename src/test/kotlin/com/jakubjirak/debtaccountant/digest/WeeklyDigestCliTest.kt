package com.jakubjirak.debtaccountant.digest

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WeeklyDigestCliTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val now = 1_713_355_200_000L

    private fun writeInventory(dir: Path): Path {
        val items = listOf<TechDebtItem>(
            item("a", priority = DebtPriority.HIGH, estimatedFixMinutes = 60, createdAtMillis = now - 86_400_000),
            item("b", priority = DebtPriority.LOW, estimatedFixMinutes = 30, createdAtMillis = now - 2 * 86_400_000)
        )
        val path = dir.resolve("inventory.json")
        Files.writeString(path, gson.toJson(items))
        return path
    }

    @Test
    fun `help exits with zero`() {
        val result = WeeklyDigestCli.run(arrayOf("--help"), now)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Usage"))
    }

    @Test
    fun `missing required args return error`() {
        val res1 = WeeklyDigestCli.run(arrayOf(), now)
        assertEquals(1, res1.exitCode)
        val res2 = WeeklyDigestCli.run(arrayOf("--period=last7"), now)
        assertEquals(1, res2.exitCode)
    }

    @Test
    fun `unknown argument rejected`() {
        val res = WeeklyDigestCli.run(arrayOf("--bogus"), now)
        assertEquals(1, res.exitCode)
        assertTrue(res.stderr.contains("Unknown argument"))
    }

    @Test
    fun `invalid period rejected`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("out.md")
        val res = WeeklyDigestCli.run(arrayOf("--period=week:junk", "--format=md", "--inventory=$inv", "--out=$out"), now)
        assertEquals(1, res.exitCode)
    }

    @Test
    fun `invalid format rejected`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("out.md")
        val res = WeeklyDigestCli.run(arrayOf("--period=last7", "--format=xml", "--inventory=$inv", "--out=$out"), now)
        assertEquals(1, res.exitCode)
    }

    @Test
    fun `produces markdown file`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("digest.md")
        val res = WeeklyDigestCli.run(
            arrayOf("--period=last7", "--format=md", "--inventory=$inv", "--out=$out", "--project=Demo"),
            now
        )
        assertEquals(0, res.exitCode, res.stderr)
        val body = Files.readString(out)
        assertTrue(body.contains("# Weekly Digest — Demo"))
        assertTrue(body.contains("Summary"))
    }

    @Test
    fun `produces html file`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("digest.html")
        val res = WeeklyDigestCli.run(
            arrayOf("--period=last14", "--format=html", "--inventory=$inv", "--out=$out"),
            now
        )
        assertEquals(0, res.exitCode)
        val body = Files.readString(out)
        assertTrue(body.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `save-snapshot writes a valid snapshot file`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("digest.md")
        val snap = dir.resolve("snap.json")
        val res = WeeklyDigestCli.run(
            arrayOf(
                "--period=last7", "--format=md",
                "--inventory=$inv", "--out=$out", "--save-snapshot=$snap"
            ),
            now
        )
        assertEquals(0, res.exitCode, res.stderr)
        assertTrue(Files.exists(snap))
        val store = DigestSnapshotStore(dir)
        val loaded = store.load(snap)
        assertEquals(2, loaded.items.size)
    }

    @Test
    fun `previous-snapshot produces delta section`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val snapStore = DigestSnapshotStore(dir)
        val prev = DigestSnapshot(
            takenAtMillis = now - 7L * 86_400_000,
            label = "previous",
            items = listOf(TechDebtItemSnapshot.from(item("a", priority = DebtPriority.HIGH), 100.0, "OK"))
        )
        val prevPath = snapStore.save(prev, "prev.json")
        val out = dir.resolve("digest.md")
        val res = WeeklyDigestCli.run(
            arrayOf(
                "--period=last7", "--format=md",
                "--inventory=$inv", "--previous-snapshot=$prevPath", "--out=$out"
            ),
            now
        )
        assertEquals(0, res.exitCode, res.stderr)
        val body = Files.readString(out)
        assertTrue(body.contains("Week-over-Week Delta"))
        assertTrue(body.contains("previous"))
    }

    @Test
    fun `invalid hourly rate rejected`(@TempDir dir: Path) {
        val inv = writeInventory(dir)
        val out = dir.resolve("d.md")
        val res = WeeklyDigestCli.run(
            arrayOf("--period=last7", "--format=md", "--inventory=$inv", "--out=$out", "--hourly-rate=abc"),
            now
        )
        assertEquals(1, res.exitCode)
    }

    @Test
    fun `missing inventory produces error`(@TempDir dir: Path) {
        val out = dir.resolve("d.md")
        val missing = dir.resolve("missing.json")
        val res = WeeklyDigestCli.run(
            arrayOf("--period=last7", "--format=md", "--inventory=$missing", "--out=$out"),
            now
        )
        assertEquals(1, res.exitCode)
        assertTrue(res.stderr.contains("Failed to read inventory"))
    }
}
