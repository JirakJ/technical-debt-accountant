package com.jakubjirak.debtaccountant.valuation

import com.jakubjirak.debtaccountant.model.DebtValuation
import com.jakubjirak.debtaccountant.model.TechDebtItem

class RoiCalculator {

    private val valuator = DollarValuator()

    fun calculateRoi(item: TechDebtItem): DebtValuation = valuator.valuate(item)

    fun prioritizeByRoi(items: List<TechDebtItem>): List<DebtValuation> {
        return valuator.valuateAll(items)
            .filter { it.monthlyInterest > 0 }
            .sortedBy { it.roiMonths }
    }

    fun getTopRoiOpportunities(items: List<TechDebtItem>, limit: Int = 5): List<DebtValuation> {
        return prioritizeByRoi(items).take(limit)
    }

    fun getBreakEvenMonths(item: TechDebtItem): Double {
        val valuation = valuator.valuate(item)
        return valuation.roiMonths
    }
}
