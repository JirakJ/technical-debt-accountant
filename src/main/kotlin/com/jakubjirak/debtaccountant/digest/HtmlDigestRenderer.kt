package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.model.DebtPriority
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a [DigestData] payload as a self-contained HTML document with inline
 * styling. The output is intentionally email-safe: no external fonts, no external
 * stylesheets, no scripts. Colors are chosen to be legible on both light and
 * dark mail clients.
 *
 * All user-supplied text is HTML-escaped.
 */
object HtmlDigestRenderer {

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    /** Produce the HTML digest for [data]. */
    fun render(data: DigestData): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"en\"><head><meta charset=\"UTF-8\">")
        sb.append("<title>Weekly Digest — ").append(esc(data.projectName)).append("</title>")
        sb.append(STYLE)
        sb.append("</head><body><div class=\"digest\">")
        sb.append("<h1>Weekly Digest — ").append(esc(data.projectName)).append("</h1>")
        sb.append("<p class=\"meta\">Period: <strong>").append(esc(data.periodLabel))
            .append("</strong> &middot; Generated: ")
            .append(esc(TIMESTAMP.format(Instant.ofEpochMilli(data.generatedAtMillis))))
            .append("</p>")
        renderSummary(sb, data)
        renderPriority(sb, data)
        renderSla(sb, data)
        renderHotspots(sb, data)
        renderRecommendations(sb, data)
        data.delta?.let { renderDelta(sb, it) }
        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun renderSummary(sb: StringBuilder, d: DigestData) {
        sb.append("<h2>Summary</h2>")
        sb.append("<table><thead><tr><th>Metric</th><th class=\"num\">Value</th></tr></thead><tbody>")
        row(sb, "Active debt items", d.totalItemCount.toString())
        row(sb, "Total estimated cost", formatDollars(d.totalDollarValue))
        row(sb, "New in period", d.newInPeriod.toString())
        row(sb, "Resolved in period", d.resolvedInPeriod.toString())
        sb.append("</tbody></table>")
    }

    private fun renderPriority(sb: StringBuilder, d: DigestData) {
        sb.append("<h2>Priority Breakdown</h2>")
        sb.append("<table><thead><tr><th>Priority</th><th class=\"num\">Count</th></tr></thead><tbody>")
        for (p in DebtPriority.values()) {
            row(sb, esc(p.displayName), (d.priorityBreakdown[p] ?: 0).toString())
        }
        sb.append("</tbody></table>")
    }

    private fun renderSla(sb: StringBuilder, d: DigestData) {
        sb.append("<h2>SLA Compliance</h2>")
        sb.append("<table><thead><tr><th>Status</th><th class=\"num\">Count</th></tr></thead><tbody>")
        row(sb, "<span class=\"ok\">OK</span>", d.sla.ok.toString())
        row(sb, "<span class=\"warn\">Approaching</span>", d.sla.approaching.toString())
        row(sb, "<span class=\"bad\">Breached</span>", d.sla.breached.toString())
        sb.append("</tbody></table>")
        if (d.sla.breached > 0) {
            sb.append("<p>Worst breach: <strong>").append(d.sla.worstBreachDays).append(" days</strong> past deadline.</p>")
        }
    }

    private fun renderHotspots(sb: StringBuilder, d: DigestData) {
        sb.append("<h2>Top Hotspots</h2>")
        if (d.hotspots.isEmpty()) {
            sb.append("<p><em>No active debt — nothing to spotlight.</em></p>")
            return
        }
        sb.append("<table><thead><tr><th>File</th><th class=\"num\">Items</th>")
            .append("<th class=\"num\">Cost</th><th>Top Priority</th></tr></thead><tbody>")
        for (h in d.hotspots) {
            sb.append("<tr><td>").append(esc(h.filePath))
                .append("</td><td class=\"num\">").append(h.itemCount)
                .append("</td><td class=\"num\">").append(formatDollars(h.totalDollarValue))
                .append("</td><td>").append(esc(h.highestPriority.displayName))
                .append("</td></tr>")
        }
        sb.append("</tbody></table>")
    }

    private fun renderRecommendations(sb: StringBuilder, d: DigestData) {
        sb.append("<h2>Sprint Recommendations</h2>")
        if (d.recommendations.isEmpty()) {
            sb.append("<p><em>No ROI-ranked recommendations available.</em></p>")
            return
        }
        sb.append("<table><thead><tr><th>ID</th><th>Description</th><th>Priority</th>")
            .append("<th class=\"num\">Effort (h)</th><th class=\"num\">Monthly $</th><th class=\"num\">ROI</th></tr></thead><tbody>")
        for (r in d.recommendations) {
            sb.append("<tr><td>").append(esc(r.itemId))
                .append("</td><td>").append(esc(r.description))
                .append("</td><td>").append(esc(r.priority.displayName))
                .append("</td><td class=\"num\">").append(formatNumber(r.fixEffortHours))
                .append("</td><td class=\"num\">").append(formatDollars(r.monthlyDollarSaved))
                .append("</td><td class=\"num\">").append(formatNumber(r.roi))
                .append("</td></tr>")
        }
        sb.append("</tbody></table>")
    }

    private fun renderDelta(sb: StringBuilder, delta: DigestDelta) {
        sb.append("<h2>Week-over-Week Delta</h2>")
        sb.append("<p>Compared against previous snapshot: <strong>").append(esc(delta.previousLabel)).append("</strong></p>")
        sb.append("<table><thead><tr><th>Metric</th><th class=\"num\">Delta</th></tr></thead><tbody>")
        row(sb, "Active item count", signed(delta.itemCountDelta))
        row(sb, "Total cost", signedDollars(delta.dollarValueDelta))
        row(sb, "Newly resolved", delta.newlyResolvedSincePrevious.toString())
        sb.append("</tbody></table>")
        sb.append("<h3>Priority mix delta</h3>")
        sb.append("<table><thead><tr><th>Priority</th><th class=\"num\">Delta</th></tr></thead><tbody>")
        for (p in DebtPriority.values()) {
            row(sb, esc(p.displayName), signed(delta.priorityMixDelta[p] ?: 0))
        }
        sb.append("</tbody></table>")
    }

    private fun row(sb: StringBuilder, label: String, value: String) {
        sb.append("<tr><td>").append(label).append("</td><td class=\"num\">").append(value).append("</td></tr>")
    }

    private fun esc(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
        return out.toString()
    }

    private fun formatDollars(d: Double): String = "$" + String.format(Locale.US, "%,.2f", d)
    private fun formatNumber(d: Double): String = String.format(Locale.US, "%.2f", d)
    private fun signed(n: Int): String = if (n >= 0) "+$n" else n.toString()
    private fun signedDollars(d: Double): String {
        val prefix = if (d >= 0) "+" else "-"
        return prefix + "$" + String.format(Locale.US, "%,.2f", kotlin.math.abs(d))
    }

    private const val STYLE = """
<style>
  .digest { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif; max-width: 860px; margin: 0 auto; padding: 16px; color: #222; }
  .digest h1 { font-size: 24px; margin: 0 0 8px 0; }
  .digest h2 { font-size: 18px; margin-top: 24px; border-bottom: 1px solid #c8c8c8; padding-bottom: 4px; }
  .digest h3 { font-size: 15px; margin-top: 16px; }
  .digest p.meta { color: #666; font-size: 13px; }
  .digest table { width: 100%; border-collapse: collapse; margin: 8px 0 16px 0; }
  .digest th, .digest td { padding: 6px 10px; border-bottom: 1px solid #d9d9d9; text-align: left; font-size: 14px; }
  .digest th { background: #f1f3f5; }
  .digest td.num, .digest th.num { text-align: right; font-variant-numeric: tabular-nums; }
  .digest .ok { color: #1b7f36; font-weight: 600; }
  .digest .warn { color: #b78100; font-weight: 600; }
  .digest .bad { color: #c0392b; font-weight: 600; }
  @media (prefers-color-scheme: dark) {
    .digest { color: #e6e6e6; }
    .digest th { background: #2a2f35; }
    .digest th, .digest td { border-bottom-color: #3a3f45; }
    .digest h2 { border-bottom-color: #3a3f45; }
    .digest .ok { color: #5ad27a; }
    .digest .warn { color: #f1c65b; }
    .digest .bad { color: #ff7566; }
    .digest p.meta { color: #9aa0a6; }
  }
</style>
"""
}
