package com.jakubjirak.debtaccountant

import com.jakubjirak.debtaccountant.valuation.DollarValuator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DollarValuatorTest {

    private val valuator = DollarValuator()

    @Test
    fun `format currency USD`() {
        val formatted = valuator.formatCurrency(100.0, "USD")
        assertTrue(formatted.contains("100"))
    }

    @Test
    fun `format currency EUR`() {
        val formatted = valuator.formatCurrency(50.5, "EUR")
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun `format currency handles unknown code gracefully`() {
        val formatted = valuator.formatCurrency(25.0, "XYZ")
        assertNotNull(formatted)
    }
}
