package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DigestSnapshotStoreTest {

    @Test
    fun `round-trip preserves snapshot data`(@TempDir dir: Path) {
        val store = DigestSnapshotStore(dir)
        val snapshot = DigestSnapshot(
            takenAtMillis = 1_713_355_200_000L,
            label = "Last 7 days",
            items = listOf(
                TechDebtItemSnapshot.from(item("a", priority = DebtPriority.HIGH), 123.45, "OK"),
                TechDebtItemSnapshot.from(item("b", priority = DebtPriority.CRITICAL), 456.0, "BREACHED")
            )
        )
        val path = store.save(snapshot)
        assertTrue(Files.exists(path))
        val loaded = store.load(path)
        assertEquals(snapshot.takenAtMillis, loaded.takenAtMillis)
        assertEquals(snapshot.label, loaded.label)
        assertEquals(2, loaded.items.size)
        assertEquals("a", loaded.items[0].id)
        assertEquals(DebtPriority.HIGH, loaded.items[0].priority)
        assertEquals(123.45, loaded.items[0].dollarValue)
        assertEquals("BREACHED", loaded.items[1].slaStatus)
    }

    @Test
    fun `default file name is date and slug`(@TempDir dir: Path) {
        val store = DigestSnapshotStore(dir)
        val snapshot = DigestSnapshot(
            takenAtMillis = 1_713_355_200_000L,
            label = "Last 7 days",
            items = emptyList()
        )
        val path = store.save(snapshot)
        val name = path.fileName.toString()
        assertTrue(name.startsWith("2024-04-17_"))
        assertTrue(name.endsWith(".json"))
        assertTrue(name.contains("last-7-days"))
    }

    @Test
    fun `listSnapshots returns sorted by mtime`(@TempDir dir: Path) {
        val store = DigestSnapshotStore(dir)
        val first = store.save(DigestSnapshot(1_000_000_000L, "first", emptyList()), "01.json")
        Thread.sleep(20)
        val second = store.save(DigestSnapshot(2_000_000_000L, "second", emptyList()), "02.json")
        val files = store.listSnapshots()
        assertEquals(listOf(first, second), files)
        assertNotNull(store.latest())
        assertEquals("second", store.latest()!!.label)
    }

    @Test
    fun `atomic write leaves no tmp file on success`(@TempDir dir: Path) {
        val store = DigestSnapshotStore(dir)
        store.save(DigestSnapshot(1L, "x", emptyList()))
        val leftovers = Files.list(dir).use { it.toList() }.filter { it.fileName.toString().endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "No *.tmp file should remain")
    }

    @Test
    fun `empty base directory yields empty list`(@TempDir dir: Path) {
        val sub = dir.resolve("does-not-exist")
        val store = DigestSnapshotStore(sub)
        assertEquals(emptyList<Path>(), store.listSnapshots())
    }
}
