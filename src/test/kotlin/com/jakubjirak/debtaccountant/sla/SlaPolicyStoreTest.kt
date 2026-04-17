package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SlaPolicyStoreTest {

    @Test
    fun `load returns DEFAULT when file missing`(@TempDir tmp: Path) {
        val loaded = SlaPolicyStore.load(tmp)
        assertEquals(SlaPolicy.DEFAULT, loaded)
    }

    @Test
    fun `save then load round-trips`(@TempDir tmp: Path) {
        val policy = SlaPolicy(
            rules = listOf(
                SlaRule(DebtPriority.HIGH, maxAgeDays = 14, warnAtPercent = 50,
                    escalateOnBreach = true, escalatedPriority = DebtPriority.CRITICAL),
                SlaRule(DebtPriority.MEDIUM, maxAgeDays = 60, warnAtPercent = 75),
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 180, warnAtPercent = 90),
        )
        SlaPolicyStore.save(policy, tmp)
        val loaded = SlaPolicyStore.load(tmp)
        assertEquals(policy, loaded)
    }

    @Test
    fun `save leaves no tmp file on happy path`(@TempDir tmp: Path) {
        SlaPolicyStore.save(SlaPolicy.DEFAULT, tmp)
        val tmpFile = tmp.resolve(SlaPolicyStore.FILE_NAME + ".tmp")
        assertFalse(Files.exists(tmpFile), "tmp file should not remain: $tmpFile")
        assertTrue(Files.exists(tmp.resolve(SlaPolicyStore.FILE_NAME)))
    }

    @Test
    fun `load of malformed JSON yields DEFAULT`(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve(SlaPolicyStore.FILE_NAME), "{not-json")
        assertEquals(SlaPolicy.DEFAULT, SlaPolicyStore.load(tmp))
    }

    @Test
    fun `toJson and fromJson round trip`() {
        val json = SlaPolicyStore.toJson(SlaPolicy.DEFAULT)
        assertEquals(SlaPolicy.DEFAULT, SlaPolicyStore.fromJson(json))
    }
}
