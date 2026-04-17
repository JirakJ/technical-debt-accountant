package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority

/**
 * A single observation of aggregate debt metrics at a specific moment in time.
 *
 * Produced by [SnapshotHistoryLoader] from persisted digest snapshots, and
 * consumed by [DebtForecaster] to build linear/exponential trend projections.
 *
 * @property timestamp epoch millis (UTC) when the underlying snapshot was captured
 * @property count total number of unresolved debt items at that moment
 * @property dollars total valuation (USD) across the inventory
 * @property byPriority item count broken down per [DebtPriority]
 */
data class TimeSeriesPoint(
    val timestamp: Long,
    val count: Int,
    val dollars: Double,
    val byPriority: Map<DebtPriority, Int>
)
