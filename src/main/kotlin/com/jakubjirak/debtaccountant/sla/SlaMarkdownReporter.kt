package com.jakubjirak.debtaccountant.sla

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

        if (eval.breached.isNotEmpty()) {
            sb.appendLine("## 🔴 Breached (${eval.breached.size})")
            sb.appendLine()
            sb.appendLine("| ID | Description | Priority | Age (days) | Over by | Owner | Path |")
            sb.appendLine("|----|-------------|----------|-----------:|--------:|-------|------|")
            for (v in eval.breached) {
                val it = v.item
                sb.append("| ").append(escape(it.id))
                    .append(" | ").append(escape(it.description.take(80)))
                    .append(" | ").append(it.priority.displayName)
                    .append(" | ").append(v.ageDays)
                    .append(" | ").append(v.daysOver)
                    .append(" | ").append(escape(it.author.ifBlank { "—" }))
                    .append(" | ").append(escape(it.filePath))
                    .append(" |")
                sb.appendLine()
            }
            sb.appendLine()
        }

        if (eval.approaching.isNotEmpty()) {
            sb.appendLine("## 🟡 Approaching (${eval.approaching.size})")
            sb.appendLine()
            sb.appendLine("| ID | Description | Priority | Age (days) | Days to breach | Owner | Path |")
            sb.appendLine("|----|-------------|----------|-----------:|---------------:|-------|------|")
            for (w in eval.approaching) {
                val it = w.item
                sb.append("| ").append(escape(it.id))
                    .append(" | ").append(escape(it.description.take(80)))
                    .append(" | ").append(it.priority.displayName)
                    .append(" | ").append(w.ageDays)
                    .append(" | ").append(w.daysToBreach)
                    .append(" | ").append(escape(it.author.ifBlank { "—" }))
                    .append(" | ").append(escape(it.filePath))
                    .append(" |")
                sb.appendLine()
            }
            sb.appendLine()
        }

        if (eval.escalations.isNotEmpty()) {
            sb.appendLine("## ⬆️ Escalations (${eval.escalations.size})")
            sb.appendLine()
            for (e in eval.escalations) {
                sb.append("- `").append(escape(e.item.id)).append("` ")
                    .append(e.from.displayName).append(" → ").append(e.to.displayName)
                    .append(" — ").appendLine(escape(e.item.description.take(100)))
            }
            sb.appendLine()
        }

        sb.appendLine("## Policy")
        sb.appendLine()
        sb.appendLine("| Priority | Max age (days) | Warn at % | Escalate on breach | Escalated priority |")
        sb.appendLine("|----------|---------------:|----------:|--------------------|--------------------|")
        for (rule in eval.policy.rules) {
            sb.append("| ").append(rule.priority.displayName)
                .append(" | ").append(rule.maxAgeDays)
                .append(" | ").append(rule.warnAtPercent)
                .append(" | ").append(if (rule.escalateOnBreach) "yes" else "no")
                .append(" | ").append(rule.escalatedPriority?.displayName ?: "—")
                .append(" |")
            sb.appendLine()
        }
        sb.append("| _default_ | ").append(eval.policy.defaultRule.maxAgeDays)
            .append(" | ").append(eval.policy.defaultRule.warnAtPercent)
            .append(" | ").append(if (eval.policy.defaultRule.escalateOnBreach) "yes" else "no")
            .append(" | ").append(eval.policy.defaultRule.escalatedPriority?.displayName ?: "—")
            .appendLine(" |")
        sb.appendLine()
        sb.appendLine("Policy version: `${eval.summary.policyVersion.take(12)}`")
        sb.appendLine()
        sb.appendLine("---")
        sb.append("_Evaluated at ").append(TIMESTAMP.format(eval.summary.evaluatedAt)).appendLine("._")
        return sb.toString()
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
