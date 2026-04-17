package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.digest.DigestSnapshot
import com.jakubjirak.debtaccountant.digest.DigestSnapshotStore
import com.jakubjirak.debtaccountant.model.DebtPriority
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset

/**
 * Converts persisted [DigestSnapshot]s into a chronological list of
 * [TimeSeriesPoint]s consumed by [DebtForecaster].
 *
 * Responsibilities:
 * 1. Read every `*.json` snapshot in a directory via [DigestSnapshotStore].
 * 2. Aggregate each snapshot into a single point (total count, total dollars,
 *    per-priority counts). Resolved items are excluded — forecasting targets
 *    the *open* debt that still needs action.
 * 3. Sort chronologically by `takenAtMillis`.
 * 4. Deduplicate: when multiple snapshots fall on the same UTC calendar date,
 *    keep only the one with the latest `takenAtMillis`.
 */
class SnapshotHistoryLoader(private val store: DigestSnapshotStore) {

    constructor(directory: Path) : this(DigestSnapshotStore(directory))

    /** Load and transform all snapshots. Never returns `null`. */
    fun load(): List<TimeSeriesPoint> {
        val paths = store.listSnapshots()
        if (paths.isEmpty()) return emptyList()
        val points = paths.mapNotNull { path ->
            runCatching { store.load(path) }.getOrNull()?.let(::toPoint)
        }
        return dedupSameDay(points).sortedBy { it.timestamp }
    }

    private fun toPoint(snapshot: DigestSnapshot): TimeSeriesPoint {
        val open = snapshot.items.filterNot { it.resolved }
        val byPriority = DebtPriority.entries.associateWith { p -> open.count { it.priority == p } }
        val dollars = open.sumOf { it.dollarValue }
        return TimeSeriesPoint(
            timestamp = snapshot.takenAtMillis,
            count = open.size,
            dollars = dollars,
            byPriority = byPriority
        )
    }

    private fun dedupSameDay(points: List<TimeSeriesPoint>): List<TimeSeriesPoint> {
        if (points.size < 2) return points
        val byDate = LinkedHashMap<String, TimeSeriesPoint>()
        for (p in points.sortedBy { it.timestamp }) {
            val date = Instant.ofEpochMilli(p.timestamp).atOffset(ZoneOffset.UTC).toLocalDate().toString()
            val existing = byDate[date]
            if (existing == null || p.timestamp >= existing.timestamp) {
                byDate[date] = p
            }
        }
        return byDate.values.toList()
    }
}
