package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DebtForecasterTest {

    private val weekMillis = 7L * 24 * 60 * 60 * 1000

    private fun series(start: Instant, counts: List<Int>): List<TimeSeriesPoint> =
        counts.mapIndexed { i, c ->
            TimeSeriesPoint(
                timestamp = start.toEpochMilli() + i * weekMillis,
                count = c,
                dollars = c * 100.0,
                byPriority = mapOf(
                    DebtPriority.CRITICAL to (c / 4),
                    DebtPriority.HIGH to (c / 3),
                    DebtPriority.MEDIUM to (c / 3),
                    DebtPriority.LOW to (c - c / 4 - 2 * (c / 3))
                )
            )
        }

    @Test
    fun `empty input returns empty`() {
        val f = DebtForecaster().forecast(emptyList(), 4)
        assertEquals(0, f.sampleCount)
        assertTrue(f.projections.isEmpty())
    }

    @Test
    fun `improving series yields IMPROVING and a burndown date`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        // 50 items decreasing by 3 each week → slope = -3
        val pts = series(start, listOf(50, 47, 44, 41, 38, 35, 32, 29, 26, 23, 20, 17))
        val f = DebtForecaster().forecast(pts, horizonWeeks = 8, model = ForecastModel.LINEAR)
        assertEquals(TrendDirection.IMPROVING, f.direction)
        assertNotNull(f.burndownDate)
        assertTrue(f.burndownDate!!.isAfter(start))
        assertEquals(8, f.projections.size)
        assertEquals(-3.0, f.linearCount.slope, 1e-6)
        // projection below current
        assertTrue(f.projections.first().predictedCount < f.currentCount)
    }

    @Test
    fun `degrading series yields DEGRADING and no burndown`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val pts = series(start, listOf(10, 13, 16, 19, 22, 25, 28, 31))
        val f = DebtForecaster().forecast(pts, horizonWeeks = 4, model = ForecastModel.LINEAR)
        assertEquals(TrendDirection.DEGRADING, f.direction)
        assertNull(f.burndownDate)
        assertTrue(f.linearCount.slope > 0)
    }

    @Test
    fun `flat series yields FLAT`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val pts = series(start, List(10) { 20 })
        val f = DebtForecaster().forecast(pts, horizonWeeks = 4, model = ForecastModel.LINEAR)
        assertEquals(TrendDirection.FLAT, f.direction)
        assertNull(f.burndownDate)
    }

    @Test
    fun `WoW delta reported`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val pts = series(start, listOf(10, 12, 15, 18, 25))
        val f = DebtForecaster().forecast(pts, horizonWeeks = 2)
        // last 25, 1 week before = 18 → delta = 7
        assertEquals(7, f.wowCountDelta)
    }

    @Test
    fun `horizonWeeks zero throws`() {
        try {
            DebtForecaster().forecast(series(Instant.EPOCH, listOf(1, 2)), 0)
            throw AssertionError("expected IAE")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }

    @Test
    fun `AUTO picks exponential when fit is better`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val counts = (0..9).map { i -> (2.0 * Math.exp(0.3 * i)).toInt().coerceAtLeast(1) }
        val pts = series(start, counts)
        val f = DebtForecaster().forecast(pts, horizonWeeks = 4, model = ForecastModel.AUTO)
        // exponential should win (r² typically higher for exp-shaped data)
        assertTrue(f.exponentialCount.r2 >= f.linearCount.r2 - 1e-6)
        assertEquals(ForecastModel.EXPONENTIAL, f.selectedModel)
    }

    @Test
    fun `priority trends computed for each priority`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val pts = series(start, listOf(20, 22, 24, 26, 28))
        val f = DebtForecaster().forecast(pts, horizonWeeks = 2)
        for (p in DebtPriority.entries) {
            assertNotNull(f.priorityTrends[p], "missing trend for $p")
        }
    }

    @Test
    fun `projections respect horizonWeeks`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val pts = series(start, listOf(5, 6, 7, 8))
        val f = DebtForecaster().forecast(pts, horizonWeeks = 20)
        assertEquals(20, f.projections.size)
        assertEquals(1, f.projections.first().weekIndex)
        assertEquals(20, f.projections.last().weekIndex)
    }
}
