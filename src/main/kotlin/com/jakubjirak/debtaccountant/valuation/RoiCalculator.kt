package com.jakubjirak.debtaccountant.valuation

import com.jakubjirak.debtaccountant.model.DebtValuation
import com.jakubjirak.debtaccountant.model.TechDebtItem

/** Ranks debt items by how quickly fixing them pays back the recurring workaround cost. */
class RoiCalculator(private val valuator: DollarValuator = DollarValuator()) {

    /** Items with recurring interest, fastest break-even first. Items with no interest never pay back and are dropped. */
    fun prioritizeByRoi(items: List<TechDebtItem>): List<DebtValuation> {
        return valuator.valuateAll(items)
            .filter { it.monthlyInterest > 0 }
            .sortedBy { it.roiMonths }
    }

    fun getTopRoiOpportunities(items: List<TechDebtItem>, limit: Int = 5): List<DebtValuation> {
        return prioritizeByRoi(items).take(limit)
    }
}
