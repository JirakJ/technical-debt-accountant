package com.jakubjirak.debtaccountant.burndown

import com.jakubjirak.debtaccountant.model.DebtTrend
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BurndownCalculator {

    data class BurndownPoint(
        val date: LocalDate,
        val activeCount: Int,
        val resolvedCount: Int,
        val cumulativeActive: Int
    )

    fun calculateBurndown(items: List<TechDebtItem>, days: Int = 30): List<BurndownPoint> {
        val now = LocalDate.now()
        val startDate = now.minusDays(days.toLong())
        val points = mutableListOf<BurndownPoint>()

        for (day in 0..days) {
            val date = startDate.plusDays(day.toLong())
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val createdBefore = items.count { it.createdAt <= endOfDay }
            val resolvedBefore = items.count {
                it.isResolved && (it.resolvedAt ?: Long.MAX_VALUE) <= endOfDay
            }
            val active = createdBefore - resolvedBefore

            val createdOnDay = items.count {
                val created = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                created == date
            }
            val resolvedOnDay = items.count {
                if (!it.isResolved || it.resolvedAt == null) return@count false
                val resolved = Instant.ofEpochMilli(it.resolvedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                resolved == date
            }

            points.add(BurndownPoint(date, createdOnDay, resolvedOnDay, active))
        }
        return points
    }

    fun detectTrend(items: List<TechDebtItem>): DebtTrend {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val sixtyDaysAgo = now - 60L * 24 * 60 * 60 * 1000

        val recentCreated = items.count { it.createdAt in thirtyDaysAgo..now }
        val recentResolved = items.count {
            it.isResolved && (it.resolvedAt ?: 0) in thirtyDaysAgo..now
        }
        val previousCreated = items.count { it.createdAt in sixtyDaysAgo..thirtyDaysAgo }
        val previousResolved = items.count {
            it.isResolved && (it.resolvedAt ?: 0) in sixtyDaysAgo..thirtyDaysAgo
        }

        val recentNet = recentCreated - recentResolved
        val previousNet = previousCreated - previousResolved

        return when {
            recentNet < previousNet -> DebtTrend.IMPROVING
            recentNet > previousNet -> DebtTrend.WORSENING
            else -> DebtTrend.STABLE
        }
    }

    fun exportCsv(points: List<BurndownPoint>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,New Items,Resolved Items,Cumulative Active")
        points.forEach { point ->
            sb.appendLine("${point.date},${point.activeCount},${point.resolvedCount},${point.cumulativeActive}")
        }
        return sb.toString()
    }
}
