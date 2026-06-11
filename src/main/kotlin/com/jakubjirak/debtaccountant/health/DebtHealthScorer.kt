package com.jakubjirak.debtaccountant.health

import com.jakubjirak.debtaccountant.model.TechDebtItem
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Computes a project [DebtHealth] grade from a debt inventory.
 *
 * The score uses exponential decay over *weighted severity* — the sum of
 * [com.jakubjirak.debtaccountant.model.DebtPriority] weights across active
 * items. This gives intuitive, bounded behaviour:
 *
 *  - an empty / fully-resolved inventory scores **100 (A)**;
 *  - a single critical item (weight 4) costs only a few points;
 *  - pressure accumulates smoothly and the score never goes below 0 or
 *    above 100, so the grade can't be "gamed" into a negative number.
 *
 * The math is pure (no IntelliJ or settings dependencies) so it is fully
 * unit-testable and reusable from the status bar widget, reports, and CI.
 */
object DebtHealthScorer {

    /**
     * Controls how quickly the score decays as weighted severity grows. With
     * the default, ~17 units of severity (e.g. ~4 critical or ~8 medium items)
     * halves the score from 100 to ~50.
     */
    private const val DECAY = 25.0

    /**
     * Scores [items], optionally factoring a monthly debt [budgetDollars] and the
     * inventory's [monthlyInterest]. The budget only sets the [DebtHealth.overBudget]
     * flag; it never changes the grade so the grade stays comparable across projects.
     */
    fun score(
        items: List<TechDebtItem>,
        monthlyInterest: Double = 0.0,
        budgetDollars: Double = 0.0
    ): DebtHealth {
        val active = items.filter { !it.isResolved }
        val weightedSeverity = active.sumOf { it.priority.weight }
        val rawScore = 100.0 * exp(-weightedSeverity / DECAY)
        val score = rawScore.roundToInt().coerceIn(0, 100)
        val overBudget = budgetDollars > 0.0 && monthlyInterest > budgetDollars

        return DebtHealth(
            score = score,
            grade = DebtGrade.fromScore(score),
            activeItems = active.size,
            weightedSeverity = weightedSeverity,
            monthlyInterest = monthlyInterest,
            budgetDollars = budgetDollars,
            overBudget = overBudget
        )
    }
}
