package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.model.DebtPriority
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a [DigestData] payload as a GitHub-Flavored Markdown document.
 *
 * The document is organized into six sections:
 *  1. Summary
 *  2. Priority Breakdown
 *  3. SLA Compliance
 *  4. Top Hotspots
 *  5. Sprint Recommendations
 *  6. Week-over-Week Delta (omitted when no previous snapshot is supplied)
 */
object MarkdownDigestRenderer {

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    /** Produce the Markdown digest for [data]. */
    fun render(data: DigestData): String {
        val sb = StringBuilder()
        sb.appendLine("# Weekly Digest — ${escape(data.projectName)}")
        sb.appendLine()
        val generated = TIMESTAMP.format(Instant.ofEpochMilli(data.generatedAtMillis))
        sb.appendLine("_Period: **${escape(data.periodLabel)}** · Generated: ${generated}_")
        sb.appendLine()
        renderSummary(sb, data)
        renderPriority(sb, data)
        renderSla(sb, data)
        renderHotspots(sb, data)
        renderRecommendations(sb, data)
        data.delta?.let { renderDelta(sb, it) }
        return sb.toString()
    }

    private fun renderSummary(sb: StringBuilder, data: DigestData) {
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("| Metric | Value |")
        sb.appendLine("| --- | ---: |")
        sb.appendLine("| Active debt items | ${data.totalItemCount} |")
        sb.appendLine("| Total estimated cost | ${formatDollars(data.totalDollarValue)} |")
        sb.appendLine("| New in period | ${data.newInPeriod} |")
        sb.appendLine("| Resolved in period | ${data.resolvedInPeriod} |")
        sb.appendLine()
    }

    private fun renderPriority(sb: StringBuilder, data: DigestData) {
        sb.appendLine("## Priority Breakdown")
        sb.appendLine()
        sb.appendLine("| Priority | Count |")
        sb.appendLine("| --- | ---: |")
        for (p in DebtPriority.values()) {
            sb.appendLine("| ${p.displayName} | ${data.priorityBreakdown[p] ?: 0} |")
        }
        sb.appendLine()
    }

    private fun renderSla(sb: StringBuilder, data: DigestData) {
        sb.appendLine("## SLA Compliance")
        sb.appendLine()
        sb.appendLine("| Status | Count |")
        sb.appendLine("| --- | ---: |")
        sb.appendLine("| 🟢 OK | ${data.sla.ok} |")
        sb.appendLine("| 🟡 Approaching | ${data.sla.approaching} |")
        sb.appendLine("| 🔴 Breached | ${data.sla.breached} |")
        sb.appendLine()
        if (data.sla.breached > 0) {
            sb.appendLine("Worst breach: **${data.sla.worstBreachDays} days** past deadline.")
            sb.appendLine()
        }
    }

    private fun renderHotspots(sb: StringBuilder, data: DigestData) {
        sb.appendLine("## Top Hotspots")
        sb.appendLine()
        if (data.hotspots.isEmpty()) {
            sb.appendLine("_No active debt — nothing to spotlight._")
            sb.appendLine()
            return
        }
        sb.appendLine("| File | Items | Cost | Top Priority |")
        sb.appendLine("| --- | ---: | ---: | --- |")
        for (h in data.hotspots) {
            sb.append("| ").append(escape(h.filePath))
                .append(" | ").append(h.itemCount)
                .append(" | ").append(formatDollars(h.totalDollarValue))
                .append(" | ").append(h.highestPriority.displayName)
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun renderRecommendations(sb: StringBuilder, data: DigestData) {
        sb.appendLine("## Sprint Recommendations")
        sb.appendLine()
        if (data.recommendations.isEmpty()) {
            sb.appendLine("_No ROI-ranked recommendations available._")
            sb.appendLine()
            return
        }
        sb.appendLine("| ID | Description | Priority | Effort (h) | Monthly \$ | ROI |")
        sb.appendLine("| --- | --- | --- | ---: | ---: | ---: |")
        for (r in data.recommendations) {
            sb.append("| ").append(escape(r.itemId))
                .append(" | ").append(escape(r.description))
                .append(" | ").append(r.priority.displayName)
                .append(" | ").append(formatNumber(r.fixEffortHours))
                .append(" | ").append(formatDollars(r.monthlyDollarSaved))
                .append(" | ").append(formatNumber(r.roi))
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun renderDelta(sb: StringBuilder, delta: DigestDelta) {
        sb.appendLine("## Week-over-Week Delta")
        sb.appendLine()
        sb.appendLine("_Compared against previous snapshot: **${escape(delta.previousLabel)}**_")
        sb.appendLine()
        sb.appendLine("| Metric | Delta |")
        sb.appendLine("| --- | ---: |")
        sb.appendLine("| Active item count | ${signed(delta.itemCountDelta)} |")
        sb.appendLine("| Total cost | ${signedDollars(delta.dollarValueDelta)} |")
        sb.appendLine("| Newly resolved | ${delta.newlyResolvedSincePrevious} |")
        sb.appendLine()
        sb.appendLine("### Priority mix delta")
        sb.appendLine()
        sb.appendLine("| Priority | Delta |")
        sb.appendLine("| --- | ---: |")
        for (p in DebtPriority.values()) {
            sb.appendLine("| ${p.displayName} | ${signed(delta.priorityMixDelta[p] ?: 0)} |")
        }
        sb.appendLine()
    }

    private fun escape(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '|' -> out.append("\\|")
            '`' -> out.append("\\`")
            '[' -> out.append("\\[")
            ']' -> out.append("\\]")
            '\n', '\r' -> out.append(' ')
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
}
