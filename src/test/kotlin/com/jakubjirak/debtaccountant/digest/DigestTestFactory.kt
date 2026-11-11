package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog

internal object DigestTestFactory {

    private val nextId = java.util.concurrent.atomic.AtomicInteger()

    @Suppress("LongParameterList") // test fixture builder: every parameter has a default
    fun item(
        id: String = "id-${nextId.incrementAndGet()}",
        filePath: String = "src/${id}.kt",
        priority: DebtPriority = DebtPriority.MEDIUM,
        category: DebtCategory = DebtCategory.CODE_SMELL,
        description: String = "desc-$id",
        estimatedFixMinutes: Int = 60,
        workaroundMinutes: Int = 0,
        createdAtMillis: Long = System.currentTimeMillis(),
        updatedAtMillis: Long = createdAtMillis,
        resolved: Boolean = false,
        resolvedAtMillis: Long? = null,
        author: String = "",
        tags: Set<String> = emptySet()
    ): TechDebtItem {
        val item = TechDebtItem(
            id = id,
            filePath = filePath,
            category = category,
            priority = priority,
            description = description,
            estimatedFixMinutes = estimatedFixMinutes,
            createdAt = createdAtMillis,
            updatedAt = updatedAtMillis,
            resolvedAt = resolvedAtMillis,
            resolved = resolved,
            author = author,
            tags = tags.toMutableSet()
        )
        if (workaroundMinutes > 0) {
            item.workaroundLogs.add(
                WorkaroundLog(
                    durationMinutes = workaroundMinutes,
                    note = "log",
                    timestamp = createdAtMillis
                )
            )
        }
        return item
    }
}
