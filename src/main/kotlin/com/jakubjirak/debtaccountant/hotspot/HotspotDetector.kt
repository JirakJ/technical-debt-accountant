package com.jakubjirak.debtaccountant.hotspot

import com.jakubjirak.debtaccountant.model.FileHotspot
import com.jakubjirak.debtaccountant.model.TechDebtItem

class HotspotDetector {

    fun detectHotspots(items: List<TechDebtItem>, limit: Int = 10): List<FileHotspot> {
        val activeItems = items.filter { !it.isResolved }
        val grouped = activeItems.groupBy { it.filePath }

        return grouped.map { (filePath, fileItems) ->
            val debtCount = fileItems.size
            val totalWorkaround = fileItems.sumOf { it.totalWorkaroundMinutes }
            val totalFix = fileItems.sumOf { it.estimatedFixMinutes }
            val score = calculateScore(fileItems)

            FileHotspot(
                filePath = filePath,
                debtCount = debtCount,
                totalWorkaroundMinutes = totalWorkaround,
                totalEstimatedFixMinutes = totalFix,
                score = score
            )
        }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun calculateScore(items: List<TechDebtItem>): Double {
        return items.sumOf { item ->
            item.priority.weight * 10.0 + item.totalWorkaroundMinutes * 0.5
        }
    }
}
