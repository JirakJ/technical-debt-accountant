package com.jakubjirak.debtaccountant.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsInputTest {

    @Test
    fun `hourly rate parses valid positive numbers`() {
        assertEquals(95.5, SettingsInput.parseHourlyRate(" 95.5 "))
    }

    @Test
    fun `hourly rate falls back to default for invalid, zero, negative or non-finite input`() {
        listOf("", "abc", "0", "-10", "NaN", "Infinity").forEach {
            assertEquals(SettingsInput.DEFAULT_HOURLY_RATE, SettingsInput.parseHourlyRate(it), "input '$it'")
        }
    }

    @Test
    fun `budget parses numbers and clamps negatives to zero`() {
        assertEquals(500.0, SettingsInput.parseBudget("500"))
        assertEquals(0.0, SettingsInput.parseBudget("-1"))
    }

    @Test
    fun `budget treats invalid input as no budget`() {
        listOf("", "x", "NaN").forEach { assertEquals(0.0, SettingsInput.parseBudget(it), "input '$it'") }
    }

    @Test
    fun `default currency is supported`() {
        assertTrue(SettingsInput.DEFAULT_CURRENCY in SettingsInput.SUPPORTED_CURRENCIES)
    }
}
