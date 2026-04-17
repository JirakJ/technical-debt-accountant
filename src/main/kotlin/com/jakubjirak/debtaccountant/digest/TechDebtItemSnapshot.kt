package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem

/**
 * Immutable point-in-time record of a single [TechDebtItem]. Used by the
 * weekly digest feature for week-over-week delta computation: the previous
 * snapshot represents "last week" and is compared against the current inventory.
 *
 * All primitive fields are Gson-friendly (no nullable enums in non-null slots).
 */
data class TechDebtItemSnapshot(
    val id: String,
    val filePath: String,
    val category: DebtCategory,
    val priority: DebtPriority,
    val description: String,
    val estimatedFixMinutes: Int,
    val totalWorkaroundMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
    val resolved: Boolean,
    val author: String,
    val tags: List<String>,
    val dollarValue: Double,
    val slaStatus: String
) {
    companion object {
        /**
         * Capture a snapshot of [item]. [dollarValue] and [slaStatus] are injected
         * so the snapshot code has no dependency on the SLA or valuation engines.
         */
        fun from(
            item: TechDebtItem,
            dollarValue: Double = 0.0,
            slaStatus: String = "UNKNOWN"
        ): TechDebtItemSnapshot = TechDebtItemSnapshot(
            id = item.id,
            filePath = item.filePath,
            category = item.category,
            priority = item.priority,
            description = item.description,
            estimatedFixMinutes = item.estimatedFixMinutes,
            totalWorkaroundMinutes = item.totalWorkaroundMinutes,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            resolvedAt = item.resolvedAt,
            resolved = item.isResolved,
            author = item.author,
            tags = item.tags.toList().sorted(),
            dollarValue = dollarValue,
            slaStatus = slaStatus
        )
    }
}

/**
 * A complete digest snapshot — a timestamped collection of [TechDebtItemSnapshot]s
 * plus an optional label for human identification (e.g. "2026-W15").
 *
 * The `version` field is reserved for future schema evolution.
 */
data class DigestSnapshot(
    val takenAtMillis: Long,
    val label: String,
    val items: List<TechDebtItemSnapshot>,
    val version: Int = 1
)
