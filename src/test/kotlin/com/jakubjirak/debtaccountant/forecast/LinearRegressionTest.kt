package com.jakubjirak.debtaccountant.forecast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinearRegressionTest {

    @Test
    fun `fits y = 2x + 1 exactly`() {
        val x = DoubleArray(10) { it.toDouble() }
        val y = DoubleArray(10) { 2.0 * it + 1.0 }
        val r = LinearRegression.fit(x, y)
        assertEquals(2.0, r.slope, 1e-9)
        assertEquals(1.0, r.intercept, 1e-9)
        assertEquals(1.0, r.r2, 1e-9)
        assertEquals(0.0, r.stdErr, 1e-9)
        assertEquals(10, r.n)
        assertEquals(21.0, r.predict(10.0), 1e-9)
    }

    @Test
    fun `negative slope`() {
        val x = DoubleArray(5) { it.toDouble() }
        val y = DoubleArray(5) { -3.0 * it + 20.0 }
        val r = LinearRegression.fit(x, y)
        assertEquals(-3.0, r.slope, 1e-9)
        assertEquals(20.0, r.intercept, 1e-9)
        assertEquals(1.0, r.r2, 1e-9)
    }

    @Test
    fun `n equals zero returns empty`() {
        val r = LinearRegression.fit(DoubleArray(0), DoubleArray(0))
        assertTrue(r.slope.isNaN())
        assertEquals(0, r.n)
    }

    @Test
    fun `n equals one returns empty`() {
        val r = LinearRegression.fit(doubleArrayOf(1.0), doubleArrayOf(2.0))
        assertTrue(r.slope.isNaN())
        assertEquals(1, r.n)
    }

    @Test
    fun `constant y produces zero slope and r2 equals one`() {
        val x = DoubleArray(5) { it.toDouble() }
        val y = DoubleArray(5) { 7.0 }
        val r = LinearRegression.fit(x, y)
        assertEquals(0.0, r.slope, 1e-9)
        assertEquals(7.0, r.intercept, 1e-9)
        assertEquals(1.0, r.r2, 1e-9)
    }

    @Test
    fun `constant x produces NaN`() {
        val x = DoubleArray(5) { 2.0 }
        val y = DoubleArray(5) { it.toDouble() }
        val r = LinearRegression.fit(x, y)
        assertTrue(r.slope.isNaN())
        assertTrue(r.intercept.isNaN())
    }

    @Test
    fun `mismatched lengths throw`() {
        try {
            LinearRegression.fit(doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0))
            throw AssertionError("expected IAE")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }

    @Test
    fun `noisy data gives high r2`() {
        val x = DoubleArray(20) { it.toDouble() }
        val y = DoubleArray(20) { i -> 2.0 * i + 1.0 + (if (i % 2 == 0) 0.1 else -0.1) }
        val r = LinearRegression.fit(x, y)
        assertTrue(r.r2 > 0.999)
        assertTrue(kotlin.math.abs(r.slope - 2.0) < 0.01)
    }
}
