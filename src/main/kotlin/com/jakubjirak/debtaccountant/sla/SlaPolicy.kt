package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.DebtPriority

/**
 * A single SLA rule for a given [DebtPriority].
 *
 * @property priority Priority this rule applies to.
 * @property maxAgeDays Maximum allowed age (in days since creation) before the item is BREACHED.
 * @property warnAtPercent When `ageDays >= maxAgeDays * warnAtPercent / 100` the item is APPROACHING. 1..100.
 * @property escalateOnBreach Whether to auto-escalate priority when an item is breached.
 * @property escalatedPriority Target priority on breach (must be strictly higher than [priority] when escalating).
 */
data class SlaRule(
    val priority: DebtPriority,
    val maxAgeDays: Int,
    val warnAtPercent: Int = 80,
    val escalateOnBreach: Boolean = false,
    val escalatedPriority: DebtPriority? = null,
)

/**
 * Immutable SLA policy. Contains one rule per priority plus a [defaultRule] fallback
 * used when no priority-specific rule is defined for a given item.
 *
 * @property rules Priority-specific rules (may be empty).
 * @property defaultRule Fallback rule applied when [rules] does not contain the item's priority.
 */
data class SlaPolicy(
    val rules: List<SlaRule>,
    val defaultRule: SlaRule,
) {

    /**
     * Returns the rule applicable to the given priority: the matching [SlaRule] or [defaultRule].
     */
    fun ruleFor(priority: DebtPriority): SlaRule =
        rules.firstOrNull { it.priority == priority } ?: defaultRule

    /**
     * Validates the policy. Returns a list of human-readable error messages.
     * Empty list means the policy is valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<DebtPriority>()
        for (rule in rules) {
            if (!seen.add(rule.priority)) {
                errors += "Duplicate rule for priority ${rule.priority.name}"
            }
            validateRule(rule, errors, label = rule.priority.name)
        }
        validateRule(defaultRule, errors, label = "defaultRule(${defaultRule.priority.name})")
        return errors
    }

    private fun validateRule(rule: SlaRule, errors: MutableList<String>, label: String) {
        if (rule.maxAgeDays <= 0) {
            errors += "Rule $label: maxAgeDays must be > 0 (was ${rule.maxAgeDays})"
        }
        if (rule.warnAtPercent !in 1..100) {
            errors += "Rule $label: warnAtPercent must be in 1..100 (was ${rule.warnAtPercent})"
        }
        if (rule.escalateOnBreach) {
            val target = rule.escalatedPriority
            if (target == null) {
                errors += "Rule $label: escalateOnBreach=true but escalatedPriority is null"
            } else if (target.weight <= rule.priority.weight) {
                errors += "Rule $label: escalatedPriority ${target.name} must be higher than ${rule.priority.name}"
            }
        }
    }

    companion object {
        /**
         * Built-in defaults:
         *  - CRITICAL: 7 days, warn at 80%, no escalation (already at top).
         *  - HIGH: 30 days, warn at 80%, escalate to CRITICAL.
         *  - MEDIUM: 90 days, warn at 80%, no escalation.
         *  - LOW: 365 days, warn at 90%, no escalation.
         *
         * Default fallback rule mirrors LOW to be conservative.
         */
        val BUILTIN_DEFAULTS: SlaPolicy = SlaPolicy(
            rules = listOf(
                SlaRule(DebtPriority.CRITICAL, maxAgeDays = 7, warnAtPercent = 80),
                SlaRule(
                    priority = DebtPriority.HIGH,
                    maxAgeDays = 30,
                    warnAtPercent = 80,
                    escalateOnBreach = true,
                    escalatedPriority = DebtPriority.CRITICAL,
                ),
                SlaRule(DebtPriority.MEDIUM, maxAgeDays = 90, warnAtPercent = 80),
                SlaRule(DebtPriority.LOW, maxAgeDays = 365, warnAtPercent = 90),
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365, warnAtPercent = 90),
        )

        /** Alias for [BUILTIN_DEFAULTS]. */
        val DEFAULT: SlaPolicy = BUILTIN_DEFAULTS
    }
}
