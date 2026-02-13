package com.jakubjirak.debtaccountant.model

data class FileHotspot(
    val filePath: String,
    val debtCount: Int,
    val totalWorkaroundMinutes: Int,
    val totalEstimatedFixMinutes: Int,
    val score: Double
)
