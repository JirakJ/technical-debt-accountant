package com.jakubjirak.debtaccountant.valuation

import com.jakubjirak.debtaccountant.model.DebtValuation
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.settings.DebtAccountantSettings
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class DollarValuator {

    fun valuate(item: TechDebtItem): DebtValuation {
        val settings = DebtAccountantSettings.getInstance()
        val hourlyRate = settings.hourlyRate
        val currency = settings.currency

        val workaroundCost = item.totalWorkaroundMinutes * (hourlyRate / 60.0)
        val fixCost = item.estimatedFixMinutes * (hourlyRate / 60.0)

        val monthlyInterest = calculateMonthlyInterest(item, hourlyRate)
        val roiMonths = if (monthlyInterest > 0) fixCost / monthlyInterest else Double.MAX_VALUE

        return DebtValuation(
            itemId = item.id,
            totalWorkaroundCost = workaroundCost,
            estimatedFixCost = fixCost,
            monthlyInterest = monthlyInterest,
            roiMonths = roiMonths,
            currency = currency
        )
    }

    fun valuateAll(items: List<TechDebtItem>): List<DebtValuation> =
        items.map { valuate(it) }

    fun totalWorkaroundCost(items: List<TechDebtItem>): Double {
        val hourlyRate = DebtAccountantSettings.getInstance().hourlyRate
        return items.sumOf { it.totalWorkaroundMinutes * (hourlyRate / 60.0) }
    }

    fun totalFixCost(items: List<TechDebtItem>): Double {
        val hourlyRate = DebtAccountantSettings.getInstance().hourlyRate
        return items.sumOf { it.estimatedFixMinutes * (hourlyRate / 60.0) }
    }

    fun formatCurrency(amount: Double, currencyCode: String = "USD"): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(getLocaleForCurrency(currencyCode))
            format.currency = Currency.getInstance(currencyCode)
            format.format(amount)
        } catch (_: Exception) {
            "$${String.format(Locale.US, "%.2f", amount)}"
        }
    }

    private fun calculateMonthlyInterest(item: TechDebtItem, hourlyRate: Double): Double {
        if (item.workaroundLogs.isEmpty()) return 0.0

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val recentLogs = item.workaroundLogs.filter { it.timestamp >= thirtyDaysAgo }
        val recentMinutes = recentLogs.sumOf { it.durationMinutes }

        return recentMinutes * (hourlyRate / 60.0)
    }

    private fun getLocaleForCurrency(currencyCode: String): Locale {
        return when (currencyCode) {
            "USD" -> Locale.US
            "EUR" -> Locale.GERMANY
            "GBP" -> Locale.UK
            "CZK" -> Locale.forLanguageTag("cs-CZ")
            else -> Locale.US
        }
    }
}
