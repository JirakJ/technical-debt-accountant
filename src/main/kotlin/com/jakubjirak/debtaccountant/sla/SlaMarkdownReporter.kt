package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders an [SlaEvaluation] to a weekly-digest Markdown document.
 *
 * Output layout:
 *  1. `# Debt SLA Report — <project>` banner + traffic light summary
 *  2. Breached table (suppressed if empty)
 *  3. Approaching table (suppressed if empty)
 *  4. Escalations section (suppressed if empty)
 *  5. Policy summary
 *  6. Footer with evaluation timestamp (UTC)
 *
 * Titles and paths are markdown-escaped so pipes / backticks / asterisks cannot break tables.
 */
object SlaMarkdownReporter {

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    /** Render [eval] for [projectName]. */
    fun render(eval: SlaEvaluation, projectName: String): String {
        val sb = StringBuilder()
        sb.appendLine("# Debt SLA Report — ${escape(projectName)}")
        sb.appendLine()
        sb.append(trafficLight(eval))
        sb.appendLine()
        sb.appendLine(summaryLine(eval))
        sb.appendLine()
        appendBreached(sb, eval)
        appendApproaching(sb, eval)
        appendEscalations(sb, eval)
        appendPolicy(sb, eval)
        sb.appendLine()
        sb.appendLine("Policy version: `${eval.summary.policyVersion.take(12)}`")
        sb.appendLine()
        sb.appendLine("---")
        sb.append("_Evaluated at ").append(TIMESTAMP.format(eval.summary.evaluatedAt)).appendLine("._")
        return sb.toString()
    }

    private fun appendBreached(sb: StringBuilder, eval: SlaEvaluation) {
        if (eval.breached.isEmpty()) return
        sb.appendLine("## 🔴 Breached (${eval.breached.size})")
        sb.appendLine()
        sb.appendLine("| ID | Description | Priority | Age (days) | Over by | Owner | Path |")
        sb.appendLine("|----|-------------|----------|-----------:|--------:|-------|------|")
        for (v in eval.breached) appendItemRow(sb, v.item, v.ageDays, v.daysOver)
        sb.appendLine()
    }

    private fun appendApproaching(sb: StringBuilder, eval: SlaEvaluation) {
        if (eval.approaching.isEmpty()) return
        sb.appendLine("## 🟡 Approaching (${eval.approaching.size})")
        sb.appendLine()
        sb.appendLine("| ID | Description | Priority | Age (days) | Days to breach | Owner | Path |")
        sb.appendLine("|----|-------------|----------|-----------:|---------------:|-------|------|")
        for (w in eval.approaching) appendItemRow(sb, w.item, w.ageDays, w.daysToBreach)
        sb.appendLine()
    }

    private fun appendItemRow(sb: StringBuilder, item: TechDebtItem, ageDays: Long, days: Long) {
        sb.append("| ").append(escape(item.id))
            .append(" | ").append(escape(item.description.take(80)))
            .append(" | ").append(item.priority.displayName)
            .append(" | ").append(ageDays)
            .append(" | ").append(days)
            .append(" | ").append(escape(item.author.ifBlank { "—" }))
            .append(" | ").append(escape(item.filePath))
            .append(" |")
        sb.appendLine()
    }

    private fun appendEscalations(sb: StringBuilder, eval: SlaEvaluation) {
        if (eval.escalations.isEmpty()) return
        sb.appendLine("## ⬆️ Escalations (${eval.escalations.size})")
        sb.appendLine()
        for (e in eval.escalations) {
            sb.append("- `").append(escape(e.item.id)).append("` ")
                .append(e.from.displayName).append(" → ").append(e.to.displayName)
                .append(" — ").appendLine(escape(e.item.description.take(100)))
        }
        sb.appendLine()
    }

    private fun appendPolicy(sb: StringBuilder, eval: SlaEvaluation) {
        sb.appendLine("## Policy")
        sb.appendLine()
        sb.appendLine("| Priority | Max age (days) | Warn at % | Escalate on breach | Escalated priority |")
        sb.appendLine("|----------|---------------:|----------:|--------------------|--------------------|")
        for (rule in eval.policy.rules) appendRuleRow(sb, rule.priority.displayName, rule)
        appendRuleRow(sb, "_default_", eval.policy.defaultRule)
    }

    private fun appendRuleRow(sb: StringBuilder, label: String, rule: SlaRule) {
        sb.append("| ").append(label)
            .append(" | ").append(rule.maxAgeDays)
            .append(" | ").append(rule.warnAtPercent)
            .append(" | ").append(if (rule.escalateOnBreach) "yes" else "no")
            .append(" | ").append(rule.escalatedPriority?.displayName ?: "—")
            .appendLine(" |")
    }

    /** Public for testing. Returns the single-line banner including the traffic-light emoji. */
    fun trafficLight(eval: SlaEvaluation): String = when {
        eval.summary.breachedCount > 0 -> "🔴 **BREACHED** — ${eval.summary.breachedCount} item(s) exceed SLA\n"
        eval.summary.approachingCount > 0 -> "🟡 **AT RISK** — ${eval.summary.approachingCount} item(s) approaching SLA\n"
        else -> "🟢 **HEALTHY** — all tracked items within SLA\n"
    }

    private fun summaryLine(eval: SlaEvaluation): String {
        val s = eval.summary
        return "**${s.totalEvaluated}** evaluated · **${s.okCount}** OK · " +
            "**${s.approachingCount}** approaching · **${s.breachedCount}** breached · " +
            "**${s.escalationCount}** escalation(s) · worst breach: **${s.worstBreachDays}d**"
    }

    /** Markdown-escape pipe, backtick, asterisk, underscore, backslash, and newlines. */
    fun escape(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '|' -> sb.append("\\|")
                '`' -> sb.append("\\`")
                '*' -> sb.append("\\*")
                '_' -> sb.append("\\_")
                '\\' -> sb.append("\\\\")
                '\n', '\r' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
