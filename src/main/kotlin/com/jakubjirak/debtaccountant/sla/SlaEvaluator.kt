package com.jakubjirak.debtaccountant.sla

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** SLA evaluation status for a single debt item. */
enum class SlaStatus { OK, APPROACHING, BREACHED }

/**
 * A breached SLA item.
 *
 * @property item The offending debt item.
 * @property ageDays Current age in days (since [TechDebtItem.createdAt]).
 * @property daysOver `ageDays - maxAgeDays`, always ≥ 1.
 * @property rule The rule that was violated.
 */
data class SlaViolation(
    val item: TechDebtItem,
    val ageDays: Long,
    val daysOver: Long,
    val rule: SlaRule,
)

/**
 * An item approaching its SLA deadline (within warn window but not yet breached).
 *
 * @property daysToBreach Days remaining until breach (≥ 0).
 */
data class SlaWarning(
    val item: TechDebtItem,
    val ageDays: Long,
    val daysToBreach: Long,
    val rule: SlaRule,
)

/**
 * An escalation action produced by the evaluator (not yet applied).
 *
 * @property from Current priority.
 * @property to Target priority.
 */
data class EscalationAction(
    val item: TechDebtItem,
    val from: DebtPriority,
    val to: DebtPriority,
)

/**
 * Aggregated SLA summary counters.
 *
 * @property policyVersion Deterministic SHA-256 hash (hex) of the normalized policy JSON.
 */
data class SlaSummary(
    val totalEvaluated: Int,
    val okCount: Int,
    val approachingCount: Int,
    val breachedCount: Int,
    val escalationCount: Int,
    val avgAgeOfBreachedDays: Double,
    val worstBreachDays: Long,
    val policyVersion: String,
    val evaluatedAt: Instant,
)

/**
 * Outcome of evaluating an inventory against an [SlaPolicy].
 * All lists are deterministically sorted — see [SlaEvaluator.evaluate].
 */
data class SlaEvaluation(
    val breached: List<SlaViolation>,
    val approaching: List<SlaWarning>,
    val ok: List<TechDebtItem>,
    val escalations: List<EscalationAction>,
    val summary: SlaSummary,
    val policy: SlaPolicy,
)

/**
 * Pure-Kotlin engine that evaluates a list of [TechDebtItem] against an [SlaPolicy].
 *
 * Age is computed from [TechDebtItem.createdAt] (epoch millis, the only
 * creation-time field present on the real model) and the injected `now`
 * [Instant], using `Duration.between(...).toDays()`.
 *
 * Status rules (given `age` in days and a [SlaRule]):
 *  - BREACHED  if `age > maxAgeDays`
 *  - APPROACHING if `age >= floor(maxAgeDays * warnAtPercent / 100)` and not breached
 *  - OK otherwise
 *
 * Resolved items ([TechDebtItem.isResolved]) are excluded entirely.
 */
object SlaEvaluator {

    private val gson = GsonBuilder().create()

    /**
     * Evaluate [items] against [policy] at [now].
     *
     * @return an [SlaEvaluation] with deterministically sorted lists:
     *   - `breached` by `daysOver` desc, then `item.id` asc
     *   - `approaching` by `daysToBreach` asc, then `item.id` asc
     *   - `escalations` by `item.id` asc
     */
    fun evaluate(items: List<TechDebtItem>, policy: SlaPolicy, now: Instant): SlaEvaluation {
        val policyVersion = hashPolicy(policy)
        val breached = mutableListOf<SlaViolation>()
        val approaching = mutableListOf<SlaWarning>()
        val ok = mutableListOf<TechDebtItem>()
        val escalations = mutableListOf<EscalationAction>()

        for (item in items) {
            if (item.isResolved) continue
            val rule = policy.ruleFor(item.priority)
            val created = Instant.ofEpochMilli(item.createdAt)
            val ageDays = Duration.between(created, now).toDays().coerceAtLeast(0)
            val warnThreshold = (rule.maxAgeDays.toLong() * rule.warnAtPercent) / 100L
            when {
                ageDays > rule.maxAgeDays -> {
                    val over = ageDays - rule.maxAgeDays
                    breached += SlaViolation(item, ageDays, over, rule)
                    if (rule.escalateOnBreach && rule.escalatedPriority != null &&
                        item.priority != rule.escalatedPriority
                    ) {
                        escalations += EscalationAction(item, item.priority, rule.escalatedPriority)
                    }
                }
                ageDays >= warnThreshold -> {
                    approaching += SlaWarning(item, ageDays, rule.maxAgeDays - ageDays, rule)
                }
                else -> ok += item
            }
        }

        val sortedBreached = breached.sortedWith(
            compareByDescending<SlaViolation> { it.daysOver }.thenBy { it.item.id }
        )
        val sortedApproaching = approaching.sortedWith(
            compareBy<SlaWarning> { it.daysToBreach }.thenBy { it.item.id }
        )
        val sortedEscalations = escalations.sortedBy { it.item.id }

        val avgAge = if (sortedBreached.isEmpty()) 0.0
        else sortedBreached.sumOf { it.ageDays }.toDouble() / sortedBreached.size
        val worst = sortedBreached.maxOfOrNull { it.daysOver } ?: 0L

        val summary = SlaSummary(
            totalEvaluated = items.count { !it.isResolved },
            okCount = ok.size,
            approachingCount = sortedApproaching.size,
            breachedCount = sortedBreached.size,
            escalationCount = sortedEscalations.size,
            avgAgeOfBreachedDays = avgAge,
            worstBreachDays = worst,
            policyVersion = policyVersion,
            evaluatedAt = now,
        )

        return SlaEvaluation(
            breached = sortedBreached,
            approaching = sortedApproaching,
            ok = ok.sortedBy { it.id },
            escalations = sortedEscalations,
            summary = summary,
            policy = policy,
        )
    }

    /**
     * Deterministic SHA-256 hash (hex, lower-case) of a normalized JSON rendering of [policy].
     * Rules are sorted by [DebtPriority.name] so reordering the rule list does not change the hash.
     */
    fun hashPolicy(policy: SlaPolicy): String {
        val normalized = policy.copy(rules = policy.rules.sortedBy { it.priority.name })
        val json = gson.toJson(normalized)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
