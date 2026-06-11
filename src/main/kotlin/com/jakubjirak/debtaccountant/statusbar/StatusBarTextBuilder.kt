package com.jakubjirak.debtaccountant.statusbar

import com.jakubjirak.debtaccountant.health.DebtHealth

/**
 * Pure formatting for the status bar widget, split out from the platform
 * widget so the text/tooltip logic is fully unit-testable.
 */
object StatusBarTextBuilder {

    /** Compact label shown in the status bar, e.g. `Debt B · $1,234/mo`. */
    fun widgetText(health: DebtHealth, formattedMonthlyInterest: String): String {
        val grade = health.grade.letter
        return if (health.monthlyInterest > 0.0) {
            "Debt $grade · $formattedMonthlyInterest/mo"
        } else {
            "Debt $grade"
        }
    }

    /** Multi-line tooltip with the score, item count and budget status. */
    fun tooltip(health: DebtHealth, formattedMonthlyInterest: String, formattedBudget: String): String {
        val lines = mutableListOf(
            "Technical Debt Health: ${health.grade.letter} (${health.grade.label})",
            "Score: ${health.score}/100",
            "Active items: ${health.activeItems}"
        )
        if (health.monthlyInterest > 0.0) {
            lines += "Monthly interest: $formattedMonthlyInterest"
        }
        if (health.budgetDollars > 0.0) {
            val status = if (health.overBudget) "OVER BUDGET" else "within budget"
            lines += "Budget: $formattedBudget/mo ($status)"
        }
        lines += "Click to open the Tech Debt tool window"
        return lines.joinToString(System.lineSeparator())
    }
}
