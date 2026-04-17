package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders a [DebtForecast] as a self-contained Markdown document. The output
 * is deterministic for a given forecast, making it suitable for CI diffing.
 *
 * Sections: Executive Summary, Trend Analysis, Priority Breakdown Trends,
 * Burndown Projection (ASCII sparkline), 12-week Forecast Table, Methodology.
 */
object ForecastMarkdownReporter {

    private val DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    fun render(forecast: DebtForecast): String {
        val sb = StringBuilder()
        sb.append("# Debt Trend Forecast\n\n")
        if (forecast.sampleCount == 0) {
            sb.append("_No snapshots available — capture at least two weekly digests before forecasting._\n")
            return sb.toString()
        }

        executiveSummary(sb, forecast)
        trendAnalysis(sb, forecast)
        priorityBreakdown(sb, forecast)
        burndownProjection(sb, forecast)
        forecastTable(sb, forecast)
        methodology(sb, forecast)
        return sb.toString()
    }

    private fun executiveSummary(sb: StringBuilder, f: DebtForecast) {
        sb.append("## Executive Summary\n\n")
        sb.append("- **Generated:** ${DATE_FMT.format(f.generatedAt)}\n")
        sb.append("- **Samples:** ${f.sampleCount} " +
            "(${DATE_FMT.format(f.firstSampleAt)} → ${DATE_FMT.format(f.lastSampleAt)})\n")
        sb.append("- **Current open debt:** ${f.currentCount} items, ${dollars(f.currentDollars)}\n")
        sb.append("- **7-day avg (count):** ${num(f.runningAvg7dCount)}\n")
        sb.append("- **30-day avg (count):** ${num(f.runningAvg30dCount)}\n")
        sb.append("- **WoW change:** ${f.wowCountDelta?.let { (if (it >= 0) "+$it" else "$it") + " items" } ?: "n/a"}\n")
        sb.append("- **Trend:** ${trendBadge(f.direction)}\n")
        sb.append("- **Model:** ${f.selectedModel} (linear r²=${num(f.linearCount.r2)}, " +
            "exponential r²=${num(f.exponentialCount.r2)})\n\n")
    }

    private fun trendAnalysis(sb: StringBuilder, f: DebtForecast) {
        sb.append("## Trend Analysis\n\n")
        sb.append("- **Slope (count / week):** ${num(f.linearCount.slope)}\n")
        sb.append("- **Slope (USD / week):** ${num(f.linearDollars.slope)}\n")
        sb.append("- **R²:** ${num(f.linearCount.r2)}\n")
        sb.append("- **Residual std. error:** ${num(f.linearCount.stdErr)}\n\n")
    }

    private fun priorityBreakdown(sb: StringBuilder, f: DebtForecast) {
        sb.append("## Priority Breakdown Trends\n\n")
        sb.append("| Priority | Current | Slope (items/week) | Direction |\n")
        sb.append("|---|---:|---:|---|\n")
        for (p in DebtPriority.entries) {
            val t = f.priorityTrends[p] ?: continue
            sb.append("| ${p.displayName} | ${t.currentCount} | ${num(t.slopePerWeek)} " +
                "| ${trendBadge(t.direction)} |\n")
        }
        sb.append("\n")
    }

    private fun burndownProjection(sb: StringBuilder, f: DebtForecast) {
        sb.append("## Burndown Projection\n\n")
        if (f.burndownDate != null) {
            sb.append("At the current slope, the backlog reaches **zero on ${DATE_FMT.format(f.burndownDate)}**.\n\n")
        } else {
            sb.append("At the current slope, the backlog **never reaches zero** " +
                "(slope is flat or growing).\n\n")
        }
        sb.append("```\n")
        sb.append(sparkline(f.projections.map { it.predictedCount }))
        sb.append("\n```\n\n")
    }

    private fun forecastTable(sb: StringBuilder, f: DebtForecast) {
        sb.append("## ${f.horizonWeeks}-week Forecast\n\n")
        sb.append("| Week | Predicted count | 95% CI | Predicted USD |\n")
        sb.append("|---:|---:|---|---:|\n")
        for (p in f.projections) {
            sb.append("| ${p.weekIndex} | ${num(p.predictedCount)} " +
                "| [${num(p.predictedCountLow)}, ${num(p.predictedCountHigh)}] " +
                "| ${dollars(p.predictedDollars)} |\n")
        }
        sb.append("\n")
    }

    private fun methodology(sb: StringBuilder, f: DebtForecast) {
        sb.append("## Methodology\n\n")
        sb.append("- **Linear model:** Ordinary Least Squares fit `count = slope * week + intercept`.\n")
        sb.append("- **Exponential model:** log-linear fit `count = a * exp(b * week)`.\n")
        sb.append("- **AUTO:** selects the model with the higher r² " +
            "(linear wins ties — selected: ${f.selectedModel}).\n")
        sb.append("- **Confidence interval:** `±1.96 × residual std. error` (approximate 95%).\n")
        sb.append("- **Trend direction:** |slope| ≤ threshold ⇒ FLAT; positive ⇒ DEGRADING; " +
            "negative ⇒ IMPROVING.\n")
        sb.append("- **Running averages:** simple mean over snapshots in the trailing window.\n")
    }

    private fun sparkline(values: List<Double>): String {
        if (values.isEmpty()) return ""
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val blocks = "▁▂▃▄▅▆▇█"
        return values.joinToString("") { v ->
            val idx = ((v - min) / range * (blocks.length - 1)).toInt().coerceIn(0, blocks.length - 1)
            blocks[idx].toString()
        }
    }

    private fun num(d: Double): String = if (!d.isFinite()) "n/a" else "%.2f".format(d)
    private fun dollars(d: Double): String = if (!d.isFinite()) "n/a" else "$" + "%,.0f".format(d)
    private fun trendBadge(dir: TrendDirection): String = when (dir) {
        TrendDirection.IMPROVING -> "✅ IMPROVING"
        TrendDirection.FLAT -> "➖ FLAT"
        TrendDirection.DEGRADING -> "⚠️ DEGRADING"
    }
}
