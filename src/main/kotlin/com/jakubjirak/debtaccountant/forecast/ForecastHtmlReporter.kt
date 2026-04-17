package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders a [DebtForecast] as a self-contained HTML document (no external
 * fonts, scripts, or CDN references). Includes an inline SVG line chart with
 * a shaded 95% confidence band for the predicted-count series.
 */
object ForecastHtmlReporter {

    private val DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    fun render(forecast: DebtForecast): String {
        val sb = StringBuilder()
        sb.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<title>Debt Trend Forecast</title>")
        sb.append(style())
        sb.append("</head><body>")
        sb.append("<h1>Debt Trend Forecast</h1>")

        if (forecast.sampleCount == 0) {
            sb.append("<p><em>No snapshots available — capture at least two weekly digests before forecasting.</em></p>")
            sb.append("</body></html>")
            return sb.toString()
        }

        summary(sb, forecast)
        chart(sb, forecast)
        priorityTable(sb, forecast)
        forecastTable(sb, forecast)
        methodology(sb, forecast)
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun style(): String = """
        <style>
          body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 2rem; color: #222; }
          h1 { border-bottom: 2px solid #333; padding-bottom: 0.3rem; }
          h2 { margin-top: 2rem; color: #333; }
          table { border-collapse: collapse; margin: 0.5rem 0 1.5rem; }
          th, td { border: 1px solid #ccc; padding: 0.4rem 0.8rem; text-align: right; }
          th:first-child, td:first-child { text-align: left; }
          .badge-improving { color: #0a7; font-weight: bold; }
          .badge-flat { color: #888; font-weight: bold; }
          .badge-degrading { color: #c33; font-weight: bold; }
          .kv { list-style: none; padding: 0; }
          .kv li { padding: 0.15rem 0; }
          .chart { background: #fafafa; border: 1px solid #ddd; border-radius: 4px; padding: 0.5rem; }
        </style>
    """.trimIndent()

    private fun summary(sb: StringBuilder, f: DebtForecast) {
        sb.append("<h2>Executive Summary</h2><ul class=\"kv\">")
        sb.append("<li><b>Generated:</b> ${DATE_FMT.format(f.generatedAt)}</li>")
        sb.append("<li><b>Samples:</b> ${f.sampleCount} " +
            "(${DATE_FMT.format(f.firstSampleAt)} → ${DATE_FMT.format(f.lastSampleAt)})</li>")
        sb.append("<li><b>Current open debt:</b> ${f.currentCount} items, ${dollars(f.currentDollars)}</li>")
        sb.append("<li><b>7-day avg:</b> ${num(f.runningAvg7dCount)}</li>")
        sb.append("<li><b>30-day avg:</b> ${num(f.runningAvg30dCount)}</li>")
        sb.append("<li><b>WoW change:</b> " +
            (f.wowCountDelta?.let { "${if (it >= 0) "+" else ""}$it items" } ?: "n/a") + "</li>")
        sb.append("<li><b>Trend:</b> ${badge(f.direction)}</li>")
        sb.append("<li><b>Model:</b> ${escape(f.selectedModel.name)} " +
            "(linear r²=${num(f.linearCount.r2)}, exponential r²=${num(f.exponentialCount.r2)})</li>")
        val bd = f.burndownDate?.let { DATE_FMT.format(it) } ?: "never"
        sb.append("<li><b>Burndown date:</b> ${escape(bd)}</li>")
        sb.append("</ul>")
    }

    private fun chart(sb: StringBuilder, f: DebtForecast) {
        sb.append("<h2>Burndown Projection</h2>")
        val projections = f.projections
        if (projections.isEmpty()) {
            sb.append("<p><em>No projections available.</em></p>"); return
        }
        val w = 640.0
        val h = 220.0
        val pad = 30.0

        val highs = projections.map { it.predictedCountHigh }
        val lows = projections.map { it.predictedCountLow }
        val mids = projections.map { it.predictedCount }
        val yMin = (lows.min().coerceAtMost(mids.min())).coerceAtLeast(0.0)
        val yMax = highs.max().coerceAtLeast(mids.max()).coerceAtLeast(yMin + 1.0)

        fun xFor(i: Int): Double =
            pad + (w - 2 * pad) * (i.toDouble() / (projections.size - 1).coerceAtLeast(1))
        fun yFor(v: Double): Double {
            val clamped = v.coerceIn(yMin, yMax)
            return h - pad - (h - 2 * pad) * ((clamped - yMin) / (yMax - yMin))
        }

        sb.append("<div class=\"chart\"><svg width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\" " +
            "xmlns=\"http://www.w3.org/2000/svg\">")
        // Axes
        sb.append("<line x1=\"$pad\" y1=\"${h - pad}\" x2=\"${w - pad}\" y2=\"${h - pad}\" " +
            "stroke=\"#888\" stroke-width=\"1\"/>")
        sb.append("<line x1=\"$pad\" y1=\"$pad\" x2=\"$pad\" y2=\"${h - pad}\" " +
            "stroke=\"#888\" stroke-width=\"1\"/>")

        // Confidence band
        val band = StringBuilder("M")
        for (i in projections.indices) band.append("${xFor(i)},${yFor(highs[i])} ")
        band.append("L")
        for (i in projections.indices.reversed()) band.append("${xFor(i)},${yFor(lows[i])} ")
        band.append("Z")
        sb.append("<path d=\"$band\" fill=\"#4a90e2\" fill-opacity=\"0.15\" stroke=\"none\"/>")

        // Mid line
        val line = StringBuilder()
        for (i in projections.indices) {
            line.append(if (i == 0) "M" else "L").append("${xFor(i)},${yFor(mids[i])} ")
        }
        sb.append("<path d=\"$line\" fill=\"none\" stroke=\"#1c5fbf\" stroke-width=\"2\"/>")

        // Axis labels
        sb.append("<text x=\"$pad\" y=\"${pad - 8}\" font-size=\"11\" fill=\"#555\">" +
            "predicted count (${num(yMin)} – ${num(yMax)})</text>")
        sb.append("<text x=\"${w - pad}\" y=\"${h - 6}\" font-size=\"11\" fill=\"#555\" " +
            "text-anchor=\"end\">week +${projections.last().weekIndex}</text>")
        sb.append("</svg></div>")
    }

    private fun priorityTable(sb: StringBuilder, f: DebtForecast) {
        sb.append("<h2>Priority Breakdown Trends</h2><table>")
        sb.append("<tr><th>Priority</th><th>Current</th><th>Slope (items/week)</th><th>Direction</th></tr>")
        for (p in DebtPriority.entries) {
            val t = f.priorityTrends[p] ?: continue
            sb.append("<tr><td>${escape(p.displayName)}</td><td>${t.currentCount}</td>" +
                "<td>${num(t.slopePerWeek)}</td><td>${badge(t.direction)}</td></tr>")
        }
        sb.append("</table>")
    }

    private fun forecastTable(sb: StringBuilder, f: DebtForecast) {
        sb.append("<h2>${f.horizonWeeks}-week Forecast</h2><table>")
        sb.append("<tr><th>Week</th><th>Predicted count</th><th>95% CI low</th>" +
            "<th>95% CI high</th><th>Predicted USD</th></tr>")
        for (p in f.projections) {
            sb.append("<tr><td>+${p.weekIndex}</td><td>${num(p.predictedCount)}</td>" +
                "<td>${num(p.predictedCountLow)}</td><td>${num(p.predictedCountHigh)}</td>" +
                "<td>${dollars(p.predictedDollars)}</td></tr>")
        }
        sb.append("</table>")
    }

    private fun methodology(sb: StringBuilder, f: DebtForecast) {
        sb.append("<h2>Methodology</h2><ul>")
        sb.append("<li>Linear model: OLS <code>count = slope × week + intercept</code>.</li>")
        sb.append("<li>Exponential model: log-linear <code>count = a × exp(b × week)</code>.</li>")
        sb.append("<li>AUTO chooses the higher r² (linear wins ties). " +
            "Selected: <b>${escape(f.selectedModel.name)}</b>.</li>")
        sb.append("<li>95% confidence interval: <code>±1.96 × residual std. error</code>.</li>")
        sb.append("<li>Running averages: simple mean over the trailing window.</li>")
        sb.append("</ul>")
    }

    private fun num(d: Double): String = if (!d.isFinite()) "n/a" else "%.2f".format(d)
    private fun dollars(d: Double): String = if (!d.isFinite()) "n/a" else "$" + "%,.0f".format(d)
    private fun badge(dir: TrendDirection): String = when (dir) {
        TrendDirection.IMPROVING -> "<span class=\"badge-improving\">IMPROVING</span>"
        TrendDirection.FLAT -> "<span class=\"badge-flat\">FLAT</span>"
        TrendDirection.DEGRADING -> "<span class=\"badge-degrading\">DEGRADING</span>"
    }
    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
