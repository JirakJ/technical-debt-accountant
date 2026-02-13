package com.jakubjirak.debtaccountant.model

data class DebtReport(
    val projectName: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val totalItems: Int,
    val resolvedItems: Int,
    val activeItems: Int,
    val totalWorkaroundCost: Double,
    val totalFixCost: Double,
    val currency: String,
    val topHotspots: List<FileHotspot>,
    val topRoiOpportunities: List<DebtValuation>,
    val categoryBreakdown: Map<DebtCategory, Int>,
    val priorityBreakdown: Map<DebtPriority, Int>
)
