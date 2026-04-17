package com.jakubjirak.debtaccountant.forecast

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.digest.DigestSnapshot
import com.jakubjirak.debtaccountant.digest.DigestSnapshotStore
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

class SnapshotHistoryLoaderTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun item(id: String, priority: DebtPriority, resolved: Boolean = false, dollars: Double = 100.0) =
        TechDebtItemSnapshot(
            id = id,
            filePath = "src/$id.kt",
            category = DebtCategory.CODE_SMELL,
            priority = priority,
            description = "d",
            estimatedFixMinutes = 30,
            totalWorkaroundMinutes = 0,
            createdAt = 0L,
            updatedAt = 0L,
            resolvedAt = if (resolved) 1L else null,
            resolved = resolved,
            author = "a",
            tags = emptyList(),
            dollarValue = dollars,
            slaStatus = "OK"
        )

    private fun writeSnapshot(dir: Path, name: String, snap: DigestSnapshot) {
        Files.writeString(dir.resolve(name), gson.toJson(snap))
    }

    @Test
    fun `load empty dir returns empty`(@TempDir tmp: Path) {
        val loader = SnapshotHistoryLoader(tmp)
        assertTrue(loader.load().isEmpty())
    }

    @Test
    fun `load sorts chronologically and excludes resolved`(@TempDir tmp: Path) {
        val t0 = Instant.parse("2026-01-05T10:00:00Z").toEpochMilli()
        val t1 = Instant.parse("2026-01-12T10:00:00Z").toEpochMilli()
        val t2 = Instant.parse("2026-01-19T10:00:00Z").toEpochMilli()

        writeSnapshot(tmp, "a.json", DigestSnapshot(t2, "w3", listOf(item("x", DebtPriority.HIGH))))
        writeSnapshot(tmp, "b.json", DigestSnapshot(t0, "w1",
            listOf(item("a", DebtPriority.HIGH), item("b", DebtPriority.LOW, resolved = true))))
        writeSnapshot(tmp, "c.json", DigestSnapshot(t1, "w2",
            listOf(item("a", DebtPriority.HIGH), item("c", DebtPriority.CRITICAL))))

        val points = SnapshotHistoryLoader(tmp).load()
        assertEquals(3, points.size)
        assertEquals(listOf(t0, t1, t2), points.map { it.timestamp })
        // resolved excluded
        assertEquals(1, points[0].count)
        assertEquals(2, points[1].count)
        assertEquals(1, points[2].count)
        assertEquals(1, points[1].byPriority[DebtPriority.CRITICAL])
    }

    @Test
    fun `same-day dedup keeps latest by timestamp`(@TempDir tmp: Path) {
        val morning = Instant.parse("2026-03-01T06:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-03-01T22:00:00Z").toEpochMilli()
        val nextDay = Instant.parse("2026-03-02T10:00:00Z").toEpochMilli()

        writeSnapshot(tmp, "am.json", DigestSnapshot(morning, "am", listOf(item("a", DebtPriority.LOW))))
        // evening has MORE items and should win
        writeSnapshot(tmp, "pm.json", DigestSnapshot(evening, "pm",
            listOf(item("a", DebtPriority.LOW), item("b", DebtPriority.HIGH))))
        writeSnapshot(tmp, "next.json", DigestSnapshot(nextDay, "next", listOf(item("a", DebtPriority.LOW))))

        val points = SnapshotHistoryLoader(tmp).load()
        assertEquals(2, points.size)
        assertEquals(evening, points[0].timestamp)
        assertEquals(2, points[0].count)
        assertEquals(nextDay, points[1].timestamp)
    }

    @Test
    fun `constructor with store works`(@TempDir tmp: Path) {
        val store = DigestSnapshotStore(tmp)
        store.save(DigestSnapshot(
            takenAtMillis = 1000L,
            label = "test",
            items = listOf(item("a", DebtPriority.HIGH, dollars = 500.0))
        ))
        val points = SnapshotHistoryLoader(store).load()
        assertEquals(1, points.size)
        assertEquals(500.0, points[0].dollars, 1e-9)
    }
}
