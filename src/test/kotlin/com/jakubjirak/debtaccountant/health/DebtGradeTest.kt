package com.jakubjirak.debtaccountant.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebtGradeTest {

    @Test
    fun `perfect score is grade A`() {
        assertEquals(DebtGrade.A, DebtGrade.fromScore(100))
    }

    @Test
    fun `threshold boundaries map to the correct grade`() {
        assertEquals(DebtGrade.A, DebtGrade.fromScore(90))
        assertEquals(DebtGrade.B, DebtGrade.fromScore(89))
        assertEquals(DebtGrade.B, DebtGrade.fromScore(75))
        assertEquals(DebtGrade.C, DebtGrade.fromScore(74))
        assertEquals(DebtGrade.C, DebtGrade.fromScore(60))
        assertEquals(DebtGrade.D, DebtGrade.fromScore(59))
        assertEquals(DebtGrade.D, DebtGrade.fromScore(40))
        assertEquals(DebtGrade.F, DebtGrade.fromScore(39))
        assertEquals(DebtGrade.F, DebtGrade.fromScore(0))
    }

    @Test
    fun `out of range scores clamp to the nearest grade`() {
        assertEquals(DebtGrade.A, DebtGrade.fromScore(150))
        assertEquals(DebtGrade.F, DebtGrade.fromScore(-10))
    }
}
