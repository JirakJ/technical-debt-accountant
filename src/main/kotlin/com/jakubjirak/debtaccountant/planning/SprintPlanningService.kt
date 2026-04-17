package com.jakubjirak.debtaccountant.planning

import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem

/**
 * Reason a candidate [TechDebtItem] was not included in the recommended sprint plan.
 */
enum class DeclineReason {
    /** The item would push the cumulative effort past the allowed budget (including overflow). */
    BUDGET,

    /** The item was filtered out by tag include/exclude rules or the minimum priority threshold. */
    FILTERED,

    /** The item has no estimated effort (`estimatedFixMinutes <= 0`) and cannot be scheduled. */
    NO_EFFORT
}

/**
 * A single recommendation entry in a [SprintPlan], enriched with derived scheduling metrics.
 *
 * @property item the underlying debt item.
 * @property fixEffortHours estimated fix cost in hours (`estimatedFixMinutes / 60.0`).
 * @property monthlyInterestSavedDollars monthly cost avoided by fixing the item (from the configured valuator).
 * @property roi `monthlyInterestSavedDollars / fixEffortHours`; higher is better.
 */
data class SprintRecommendation(
    val item: TechDebtItem,
    val fixEffortHours: Double,
    val monthlyInterestSavedDollars: Double,
    val roi: Double
)

/**
 * An item that was considered but not included in the recommendation, with the reason why.
 */
data class DeclinedItem(
    val item: TechDebtItem,
    val reason: DeclineReason,
    val fixEffortHours: Double,
    val monthlyInterestSavedDollars: Double,
    val roi: Double
)

/**
 * Configuration for the [SprintPlanningService.plan] call.
 *
 * @property budgetHours total sprint budget expressed in hours (must be >= 0).
 * @property tagsIncluded if non-empty, only items whose tags intersect this set are eligible.
 * @property tagsExcluded items carrying any of these tags are filtered out.
 * @property minPriority minimum [DebtPriority] required (by `weight`).
 * @property allowOverflowOnce when true, after the greedy pass one additional highest-ROI item
 *   is added if the overflow is within 20% of the budget.
 * @property valuator functional interface mapping an item to its monthly interest in dollars.
 *   Production wiring supplies `DollarValuator().valuate(it).monthlyInterest`; tests may stub.
 */
data class SprintPlanningOptions(
    val budgetHours: Double,
    val tagsIncluded: Set<String> = emptySet(),
    val tagsExcluded: Set<String> = emptySet(),
    val minPriority: DebtPriority = DebtPriority.LOW,
    val allowOverflowOnce: Boolean = false,
    val valuator: (TechDebtItem) -> Double
)

/**
 * A plan produced by [SprintPlanningService], containing the sprint backlog plus declined items.
 *
 * @property selected chosen items, in the order they were picked (ROI desc).
 * @property declined all items that were not chosen, with the reason.
 * @property totalHours sum of [SprintRecommendation.fixEffortHours] over [selected].
 * @property totalMonthlyDollarSavings sum of [SprintRecommendation.monthlyInterestSavedDollars] over [selected].
 * @property budgetHours echo of the budget used to compute this plan.
 */
data class SprintPlan(
    val selected: List<SprintRecommendation>,
    val declined: List<DeclinedItem>,
    val totalHours: Double,
    val totalMonthlyDollarSavings: Double,
    val budgetHours: Double
) {
    /** Count of chosen recommendations. */
    val itemCount: Int get() = selected.size

    /**
     * Render this plan as a human-readable Markdown document suitable for Jira/GitHub/etc.
     * Titles and paths are escaped against markdown-table-breaking characters.
     */
    fun toMarkdown(): String = renderMarkdown(this)
}

/**
 * Pure-Kotlin service that recommends a sprint backlog from a list of [TechDebtItem]s using a
 * greedy knapsack strategy optimized for ROI (monthly interest saved per hour of fix effort).
 *
 * This class has no IntelliJ Platform dependencies and is fully unit-testable. Production
 * callers wire [SprintPlanningOptions.valuator] to the real `DollarValuator`.
 */
class SprintPlanningService {

    /**
     * Produce a [SprintPlan] for the given [items] under [options].
     *
     * Algorithm:
     * 1. Partition into eligible / declined (NO_EFFORT, FILTERED).
     * 2. Compute ROI for every eligible item.
     * 3. Sort by (roi desc, priority-weight desc, age desc [older first], id asc) for determinism.
     * 4. Greedily accept items while `cumulativeHours + itemHours <= budgetHours`.
     * 5. If [SprintPlanningOptions.allowOverflowOnce] is set, pick the single highest-ROI
     *    budget-declined item whose inclusion keeps cumulative <= `budgetHours * 1.2`.
     */
    fun plan(items: List<TechDebtItem>, options: SprintPlanningOptions): SprintPlan {
        require(options.budgetHours >= 0.0) { "budgetHours must be >= 0" }

        val declined = mutableListOf<DeclinedItem>()
        val eligible = mutableListOf<SprintRecommendation>()

        for (item in items) {
            val hours = item.estimatedFixMinutes / 60.0
            if (item.estimatedFixMinutes <= 0) {
                declined += DeclinedItem(item, DeclineReason.NO_EFFORT, hours, 0.0, 0.0)
                continue
            }
            if (!passesFilters(item, options)) {
                val dollars = safeValuate(options.valuator, item)
                declined += DeclinedItem(item, DeclineReason.FILTERED, hours, dollars, roi(dollars, hours))
                continue
            }
            val dollars = safeValuate(options.valuator, item)
            eligible += SprintRecommendation(item, hours, dollars, roi(dollars, hours))
        }

        val sorted = eligible.sortedWith(RECOMMENDATION_ORDER)

        val selected = mutableListOf<SprintRecommendation>()
        val budgetDeclined = mutableListOf<SprintRecommendation>()
        var used = 0.0
        for (rec in sorted) {
            if (used + rec.fixEffortHours <= options.budgetHours + EPSILON) {
                selected += rec
                used += rec.fixEffortHours
            } else {
                budgetDeclined += rec
            }
        }

        if (options.allowOverflowOnce && budgetDeclined.isNotEmpty()) {
            val overflowCap = options.budgetHours * OVERFLOW_FACTOR
            val candidate = budgetDeclined.first()
            if (used + candidate.fixEffortHours <= overflowCap + EPSILON) {
                selected += candidate
                used += candidate.fixEffortHours
                budgetDeclined.removeAt(0)
            }
        }

        for (rec in budgetDeclined) {
            declined += DeclinedItem(rec.item, DeclineReason.BUDGET, rec.fixEffortHours, rec.monthlyInterestSavedDollars, rec.roi)
        }

        val totalSavings = selected.sumOf { it.monthlyInterestSavedDollars }
        return SprintPlan(
            selected = selected.toList(),
            declined = declined.toList(),
            totalHours = used,
            totalMonthlyDollarSavings = totalSavings,
            budgetHours = options.budgetHours
        )
    }

    private fun passesFilters(item: TechDebtItem, options: SprintPlanningOptions): Boolean {
        if (item.isResolved) return false
        if (item.priority.weight < options.minPriority.weight) return false
        if (options.tagsExcluded.isNotEmpty() && item.tags.any { it in options.tagsExcluded }) return false
        if (options.tagsIncluded.isNotEmpty() && item.tags.none { it in options.tagsIncluded }) return false
        return true
    }

    private fun safeValuate(valuator: (TechDebtItem) -> Double, item: TechDebtItem): Double {
        val v = valuator(item)
        return if (v.isFinite() && v >= 0.0) v else 0.0
    }

    private fun roi(monthlyDollars: Double, hours: Double): Double =
        if (hours > 0.0) monthlyDollars / hours else 0.0

    companion object {
        private const val EPSILON = 1e-9
        private const val OVERFLOW_FACTOR = 1.20

        /**
         * Deterministic total ordering: ROI desc, priority-weight desc, older (smaller createdAt) first,
         * finally by `id` ascending to guarantee stability across JVMs.
         */
        internal val RECOMMENDATION_ORDER: Comparator<SprintRecommendation> =
            compareByDescending<SprintRecommendation> { it.roi }
                .thenByDescending { it.item.priority.weight }
                .thenBy { it.item.createdAt }
                .thenBy { it.item.id }
    }
}

private const val MD_BUDGET_LABEL = "Budget (hours)"
private const val MD_SELECTED_LABEL = "Selected hours"
private const val MD_SAVINGS_LABEL = "Monthly \$ saved"
private const val MD_ITEMS_LABEL = "Items selected"

private fun renderMarkdown(plan: SprintPlan): String {
    val sb = StringBuilder()
    sb.appendLine("# Sprint Planning Recommendation")
    sb.appendLine()
    sb.appendLine("## Summary")
    sb.appendLine()
    sb.appendLine("| Metric | Value |")
    sb.appendLine("| --- | --- |")
    sb.appendLine("| $MD_BUDGET_LABEL | ${formatHours(plan.budgetHours)} |")
    sb.appendLine("| $MD_SELECTED_LABEL | ${formatHours(plan.totalHours)} |")
    sb.appendLine("| $MD_SAVINGS_LABEL | ${formatDollars(plan.totalMonthlyDollarSavings)} |")
    sb.appendLine("| $MD_ITEMS_LABEL | ${plan.itemCount} |")
    sb.appendLine()
    sb.appendLine("## Selected Items")
    sb.appendLine()
    if (plan.selected.isEmpty()) {
        sb.appendLine("_No items selected._")
    } else {
        sb.appendLine("| ID | Title | Priority | Effort (h) | Monthly \$ | ROI | Path |")
        sb.appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        for (r in plan.selected) {
            sb.append("| ").append(escapeMd(r.item.id))
                .append(" | ").append(escapeMd(r.item.description))
                .append(" | ").append(escapeMd(r.item.priority.displayName))
                .append(" | ").append(formatHours(r.fixEffortHours))
                .append(" | ").append(formatDollars(r.monthlyInterestSavedDollars))
                .append(" | ").append(formatRoi(r.roi))
                .append(" | ").append(escapeMd(r.item.filePath))
                .append(" |")
                .append('\n')
        }
    }
    if (plan.declined.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("<details><summary>Declined items (${plan.declined.size})</summary>")
        sb.appendLine()
        sb.appendLine("| ID | Title | Reason | Effort (h) | Monthly \$ | Path |")
        sb.appendLine("| --- | --- | --- | --- | --- | --- |")
        for (d in plan.declined) {
            sb.append("| ").append(escapeMd(d.item.id))
                .append(" | ").append(escapeMd(d.item.description))
                .append(" | ").append(d.reason.name)
                .append(" | ").append(formatHours(d.fixEffortHours))
                .append(" | ").append(formatDollars(d.monthlyInterestSavedDollars))
                .append(" | ").append(escapeMd(d.item.filePath))
                .append(" |")
                .append('\n')
        }
        sb.appendLine()
        sb.appendLine("</details>")
    }
    return sb.toString()
}

private fun escapeMd(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (c in s) {
        when (c) {
            '|' -> sb.append("\\|")
            '[' -> sb.append("\\[")
            ']' -> sb.append("\\]")
            '`' -> sb.append("\\`")
            '\n', '\r' -> sb.append(' ')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private fun formatHours(h: Double): String = String.format(java.util.Locale.US, "%.2f", h)
private fun formatDollars(d: Double): String = String.format(java.util.Locale.US, "%.2f", d)
private fun formatRoi(r: Double): String = String.format(java.util.Locale.US, "%.2f", r)
