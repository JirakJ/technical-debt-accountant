package com.jakubjirak.debtaccountant.sla

import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SlaPolicyValidationTest {

    @Test
    fun `builtin defaults are valid`() {
        assertTrue(SlaPolicy.DEFAULT.validate().isEmpty())
    }

    @Test
    fun `duplicate priorities are rejected`() {
        val policy = SlaPolicy(
            rules = listOf(
                SlaRule(DebtPriority.HIGH, maxAgeDays = 30),
                SlaRule(DebtPriority.HIGH, maxAgeDays = 20),
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365),
        )
        val errors = policy.validate()
        assertTrue(errors.any { it.contains("Duplicate") }, "expected duplicate error: $errors")
    }

    @Test
    fun `non-positive maxAgeDays rejected`() {
        val policy = SlaPolicy(
            rules = listOf(SlaRule(DebtPriority.HIGH, maxAgeDays = 0)),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = -1),
        )
        val errors = policy.validate()
        assertEquals(2, errors.count { it.contains("maxAgeDays") })
    }

    @Test
    fun `warnAtPercent out of range rejected`() {
        val policy = SlaPolicy(
            rules = listOf(SlaRule(DebtPriority.HIGH, maxAgeDays = 30, warnAtPercent = 0)),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365, warnAtPercent = 101),
        )
        val errors = policy.validate()
        assertEquals(2, errors.count { it.contains("warnAtPercent") })
    }

    @Test
    fun `escalateOnBreach without target priority rejected`() {
        val policy = SlaPolicy(
            rules = listOf(
                SlaRule(DebtPriority.HIGH, maxAgeDays = 30, escalateOnBreach = true, escalatedPriority = null)
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365),
        )
        assertTrue(policy.validate().any { it.contains("escalatedPriority is null") })
    }

    @Test
    fun `downgrade escalation rejected`() {
        val policy = SlaPolicy(
            rules = listOf(
                SlaRule(
                    priority = DebtPriority.HIGH,
                    maxAgeDays = 30,
                    escalateOnBreach = true,
                    escalatedPriority = DebtPriority.LOW,
                )
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365),
        )
        assertTrue(policy.validate().any { it.contains("must be higher") })
    }

    @Test
    fun `same-priority escalation rejected`() {
        val policy = SlaPolicy(
            rules = listOf(
                SlaRule(
                    priority = DebtPriority.HIGH,
                    maxAgeDays = 30,
                    escalateOnBreach = true,
                    escalatedPriority = DebtPriority.HIGH,
                )
            ),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 365),
        )
        assertTrue(policy.validate().any { it.contains("must be higher") })
    }

    @Test
    fun `ruleFor returns matching rule or default`() {
        val policy = SlaPolicy.DEFAULT
        assertEquals(30, policy.ruleFor(DebtPriority.HIGH).maxAgeDays)
        assertEquals(7, policy.ruleFor(DebtPriority.CRITICAL).maxAgeDays)

        val onlyHigh = SlaPolicy(
            rules = listOf(SlaRule(DebtPriority.HIGH, maxAgeDays = 10)),
            defaultRule = SlaRule(DebtPriority.LOW, maxAgeDays = 99),
        )
        assertEquals(10, onlyHigh.ruleFor(DebtPriority.HIGH).maxAgeDays)
        assertEquals(onlyHigh.defaultRule, onlyHigh.ruleFor(DebtPriority.MEDIUM))
        assertEquals(onlyHigh.defaultRule, onlyHigh.ruleFor(DebtPriority.CRITICAL))
        assertFalse(policy.validate().isNotEmpty())
    }
}
