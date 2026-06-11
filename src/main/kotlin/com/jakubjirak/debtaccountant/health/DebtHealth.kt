package com.jakubjirak.debtaccountant.health

/**
 * Snapshot of a project's technical-debt health.
 *
 * @property score 0–100 where 100 means a pristine codebase (no active debt)
 *     and the value decays as weighted severity accumulates.
 * @property grade letter grade derived from [score].
 * @property activeItems number of unresolved debt items.
 * @property weightedSeverity sum of priority weights across active items; the
 *     raw debt pressure the score is computed from.
 * @property monthlyInterest recurring monthly dollar cost of carrying the debt.
 * @property budgetDollars configured monthly debt budget; `0.0` means no budget.
 * @property overBudget true when a budget is set and [monthlyInterest] exceeds it.
 */
data class DebtHealth(
    val score: Int,
    val grade: DebtGrade,
    val activeItems: Int,
    val weightedSeverity: Double,
    val monthlyInterest: Double = 0.0,
    val budgetDollars: Double = 0.0,
    val overBudget: Boolean = false
)
