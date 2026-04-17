package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ForecastReportersTest {

    private val weekMillis = 7L * 24 * 60 * 60 * 1000

    private fun sample(counts: List<Int>): DebtForecast {
        val start = Instant.parse("2026-02-01T00:00:00Z")
        val points = counts.mapIndexed { i, c ->
            TimeSeriesPoint(
                timestamp = start.toEpochMilli() + i * weekMillis,
                count = c,
                dollars = c * 250.0,
                byPriority = mapOf(
                    DebtPriority.CRITICAL to 1,
                    DebtPriority.HIGH to 2,
                    DebtPriority.MEDIUM to 3,
                    DebtPriority.LOW to c - 6
                )
            )
        }
        return DebtForecaster().forecast(points, horizonWeeks = 6, model = ForecastModel.LINEAR)
    }

    @Test
    fun `markdown contains required sections`() {
        val md = ForecastMarkdownReporter.render(sample(listOf(30, 28, 26, 24, 22, 20)))
        for (sec in listOf(
            "# Debt Trend Forecast",
            "## Executive Summary",
            "## Trend Analysis",
            "## Priority Breakdown Trends",
            "## Burndown Projection",
            "## Methodology",
            "## 6-week Forecast"
        )) {
            assertTrue(md.contains(sec), "missing section: $sec\n--\n$md")
        }
        // sparkline present
        assertTrue(md.contains("```"))
    }

    @Test
    fun `markdown empty forecast handled`() {
        val md = ForecastMarkdownReporter.render(DebtForecast.empty(4))
        assertTrue(md.contains("No snapshots"))
    }

    @Test
    fun `html contains svg and required sections`() {
        val html = ForecastHtmlReporter.render(sample(listOf(10, 12, 15, 18, 22, 25)))
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<svg"))
        assertTrue(html.contains("Executive Summary"))
        assertTrue(html.contains("Priority Breakdown Trends"))
        assertTrue(html.contains("6-week Forecast"))
        assertTrue(html.contains("Methodology"))
        // No external CSS/JS/font references (the only http:// allowed is the SVG xmlns URI)
        assertTrue(!html.contains("<link "))
        assertTrue(!html.contains("<script"))
        assertTrue(!html.contains("@import"))
        assertTrue(!html.contains("fonts.googleapis"))
        assertEquals(1, countOccurrences(html, "</html>"))
    }

    @Test
    fun `html empty forecast handled`() {
        val html = ForecastHtmlReporter.render(DebtForecast.empty(4))
        assertTrue(html.contains("No snapshots"))
        assertTrue(html.contains("</html>"))
    }

    private fun countOccurrences(s: String, sub: String): Int {
        var i = 0; var n = 0
        while (true) {
            val idx = s.indexOf(sub, i)
            if (idx < 0) return n
            n++; i = idx + sub.length
        }
    }
}
