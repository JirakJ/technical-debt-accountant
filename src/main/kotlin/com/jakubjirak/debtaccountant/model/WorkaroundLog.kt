package com.jakubjirak.debtaccountant.model

data class WorkaroundLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val note: String = ""
)
