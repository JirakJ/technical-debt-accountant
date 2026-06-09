package com.jakubjirak.debtaccountant.scanner

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority

/**
 * Comment markers recognized by [DebtCommentScanner], each mapped to the debt
 * category, priority, and default fix estimate used when importing.
 */
enum class DebtMarker(
    val category: DebtCategory,
    val priority: DebtPriority,
    val defaultFixMinutes: Int
) {
    FIXME(DebtCategory.CODE_SMELL, DebtPriority.HIGH, 90),
    HACK(DebtCategory.ARCHITECTURE_VIOLATION, DebtPriority.HIGH, 120),
    XXX(DebtCategory.CODE_SMELL, DebtPriority.HIGH, 90),
    OPTIMIZE(DebtCategory.PERFORMANCE_ISSUE, DebtPriority.MEDIUM, 120),
    TODO(DebtCategory.CODE_SMELL, DebtPriority.MEDIUM, 60)
}
