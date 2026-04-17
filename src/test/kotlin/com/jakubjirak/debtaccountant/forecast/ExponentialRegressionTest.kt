package com.jakubjirak.debtaccountant.forecast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

class ExponentialRegressionTest {

    @Test
    fun `recovers a=2 b=0_1`() {
        val x = DoubleArray(20) { it.toDouble() }
        val y = DoubleArray(20) { i -> 2.0 * exp(0.1 * i) }
        val r = ExponentialRegression.fit(x, y)
        assertEquals(2.0, r.a, 1e-6)
        assertEquals(0.1, r.b, 1e-6)
        assertEquals(1.0, r.r2, 1e-6)
        assertTrue(r.predict(10.0) > r.predict(5.0))
    }

    @Test
    fun `zeros and negatives dropped`() {
        val x = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val y = doubleArrayOf(0.0, -1.0, 2.0, 4.0, 8.0)
        val r = ExponentialRegression.fit(x, y)
        // only 3 positive points remain
        assertEquals(3, r.n)
        assertTrue(r.b > 0)
    }

    @Test
    fun `fewer than two positive points returns empty`() {
        val r = ExponentialRegression.fit(doubleArrayOf(0.0, 1.0), doubleArrayOf(-1.0, 5.0))
        assertTrue(r.a.isNaN())
        assertTrue(r.b.isNaN())
    }

    @Test
    fun `mismatched lengths throw`() {
        try {
            ExponentialRegression.fit(doubleArrayOf(1.0), doubleArrayOf(1.0, 2.0))
            throw AssertionError("expected IAE")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }
}
