package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SlaEvaluatorTest {

    private val now: Instant = Instant.parse("2026-04-17T12:00:00Z")

    private fun item(
        id: String,
        priority: DebtPriority,
        ageDays: Long,
        resolved: Boolean = false,
    ): TechDebtItem {
        val created = now.minusSeconds(ageDays * 86_400).toEpochMilli()
        return TechDebtItem(
            id = id,
            filePath = "src/$id.kt",
            category = DebtCategory.values().first(),
            priority = priority,
            description = "desc-$id",
            estimatedFixMinutes = 30,
            createdAt = created,
            updatedAt = created,
            resolved = resolved,
        )
    }

    private val policy: SlaPolicy = SlaPolicy(
        rules = listOf(
            SlaRule(DebtPriority.HIGH, maxAgeDays = 30, warnAtPercent = 80,
                escalateOnBreach = true, escalatedPriority = DebtPriority.CRITICAL),
            SlaRule(DebtPriority.MEDIUM, maxAgeDays = 90, warnAtPercent = 80),
        ),
        defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365, warnAtPercent = 90),
    )

    @Test
    fun `OK when well under threshold`() {
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.HIGH, 5)), policy, now)
        assertEquals(1, eval.ok.size)
        assertTrue(eval.breached.isEmpty())
        assertTrue(eval.approaching.isEmpty())
    }

    @Test
    fun `approaching exactly at warn threshold`() {
        // HIGH 30d * 80% = 24d → approaching
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.HIGH, 24)), policy, now)
        assertEquals(1, eval.approaching.size)
        val w = eval.approaching.first()
        assertEquals(24L, w.ageDays)
        assertEquals(6L, w.daysToBreach)
    }

    @Test
    fun `age equal to maxAgeDays is still OK (not breached)`() {
        // age == maxAgeDays → warn (since 30 >= 24) but NOT breached (requires >)
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.HIGH, 30)), policy, now)
        assertEquals(1, eval.approaching.size)
        assertTrue(eval.breached.isEmpty())
    }

    @Test
    fun `age greater than maxAgeDays is breached`() {
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.HIGH, 31)), policy, now)
        assertEquals(1, eval.breached.size)
        val v = eval.breached.first()
        assertEquals(1L, v.daysOver)
    }

    @Test
    fun `escalation produced when HIGH breaches and rule escalates`() {
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.HIGH, 60)), policy, now)
        assertEquals(1, eval.escalations.size)
        val esc = eval.escalations.first()
        assertEquals(DebtPriority.HIGH, esc.from)
        assertEquals(DebtPriority.CRITICAL, esc.to)
    }

    @Test
    fun `no escalation when item already at target priority`() {
        // Force a policy that would escalate CRITICAL → CRITICAL (invalid but exercise same-priority guard)
        val p = SlaPolicy(
            rules = listOf(
                SlaRule(DebtPriority.HIGH, maxAgeDays = 30, escalateOnBreach = true, escalatedPriority = DebtPriority.CRITICAL)
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365),
        )
        // Item is CRITICAL → falls through to default LOW rule (365d), age 60 → OK, no escalation
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.CRITICAL, 60)), p, now)
        assertTrue(eval.escalations.isEmpty())
    }

    @Test
    fun `no escalation when rule has escalateOnBreach false`() {
        // MEDIUM 90d, no escalation
        val eval = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.MEDIUM, 200)), policy, now)
        assertEquals(1, eval.breached.size)
        assertTrue(eval.escalations.isEmpty())
    }

    @Test
    fun `missing priority-specific rule falls through to default`() {
        // LOW has no rule → defaultRule (365d). age 300 → warn threshold = 365*0.9 = 328 → OK
        val ok = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.LOW, 300)), policy, now)
        assertEquals(1, ok.ok.size)

        // age 330 → approaching
        val warn = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.LOW, 330)), policy, now)
        assertEquals(1, warn.approaching.size)

        // age 400 → breached
        val br = SlaEvaluator.evaluate(listOf(item("a", DebtPriority.LOW, 400)), policy, now)
        assertEquals(1, br.breached.size)
    }

    @Test
    fun `resolved items are ignored`() {
        val items = listOf(
            item("a", DebtPriority.HIGH, 200, resolved = true),
            item("b", DebtPriority.HIGH, 5),
        )
        val eval = SlaEvaluator.evaluate(items, policy, now)
        assertTrue(eval.breached.isEmpty())
        assertEquals(1, eval.ok.size)
        assertEquals(1, eval.summary.totalEvaluated)
    }

    @Test
    fun `deterministic sort breached by daysOver desc then id`() {
        val items = listOf(
            item("c", DebtPriority.HIGH, 35),  // over 5
            item("a", DebtPriority.HIGH, 50),  // over 20
            item("b", DebtPriority.HIGH, 35),  // over 5
        )
        val eval = SlaEvaluator.evaluate(items, policy, now)
        assertEquals(listOf("a", "b", "c"), eval.breached.map { it.item.id })
    }

    @Test
    fun `deterministic sort approaching by daysToBreach asc`() {
        val items = listOf(
            item("a", DebtPriority.HIGH, 24), // toBreach 6
            item("b", DebtPriority.HIGH, 28), // toBreach 2
            item("c", DebtPriority.HIGH, 26), // toBreach 4
        )
        val eval = SlaEvaluator.evaluate(items, policy, now)
        assertEquals(listOf("b", "c", "a"), eval.approaching.map { it.item.id })
    }

    @Test
    fun `summary math - counts and avg breach age and worst`() {
        val items = listOf(
            item("a", DebtPriority.HIGH, 40),  // over 10, age 40
            item("b", DebtPriority.HIGH, 60),  // over 30, age 60
            item("c", DebtPriority.HIGH, 10),  // ok
            item("d", DebtPriority.HIGH, 25),  // approaching
        )
        val eval = SlaEvaluator.evaluate(items, policy, now)
        val s = eval.summary
        assertEquals(4, s.totalEvaluated)
        assertEquals(1, s.okCount)
        assertEquals(1, s.approachingCount)
        assertEquals(2, s.breachedCount)
        assertEquals(50.0, s.avgAgeOfBreachedDays, 0.0001)
        assertEquals(30L, s.worstBreachDays)
    }

    @Test
    fun `policyVersion changes when policy changes`() {
        val h1 = SlaEvaluator.hashPolicy(policy)
        val modified = policy.copy(
            defaultRule = policy.defaultRule.copy(maxAgeDays = policy.defaultRule.maxAgeDays + 1)
        )
        val h2 = SlaEvaluator.hashPolicy(modified)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `policyVersion stable under rule reorder`() {
        val reordered = policy.copy(rules = policy.rules.reversed())
        assertEquals(SlaEvaluator.hashPolicy(policy), SlaEvaluator.hashPolicy(reordered))
    }

    @Test
    fun `empty input yields empty evaluation with zero summary`() {
        val eval = SlaEvaluator.evaluate(emptyList(), policy, now)
        assertEquals(0, eval.summary.totalEvaluated)
        assertEquals(0.0, eval.summary.avgAgeOfBreachedDays, 0.0001)
        assertEquals(0L, eval.summary.worstBreachDays)
    }
}
