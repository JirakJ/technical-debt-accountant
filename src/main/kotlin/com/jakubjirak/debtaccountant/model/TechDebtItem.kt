package com.jakubjirak.debtaccountant.model

data class TechDebtItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val filePath: String,
    val lineStart: Int = 0,
    val lineEnd: Int = 0,
    val category: DebtCategory,
    val priority: DebtPriority = DebtPriority.MEDIUM,
    val description: String,
    val estimatedFixMinutes: Int = 60,
    val workaroundLogs: MutableList<WorkaroundLog> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val author: String = "",
    val linkedCommit: String? = null,
    val tags: MutableSet<String> = mutableSetOf(),
    val resolved: Boolean = false
) {
    val totalWorkaroundMinutes: Int
        get() = workaroundLogs.sumOf { it.durationMinutes }

    val isResolved: Boolean
        get() = resolved || resolvedAt != null
}
