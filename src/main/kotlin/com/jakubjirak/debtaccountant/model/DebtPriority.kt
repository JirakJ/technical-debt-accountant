package com.jakubjirak.debtaccountant.model

enum class DebtPriority(val weight: Double, val displayName: String) {
    CRITICAL(4.0, "Critical"),
    HIGH(3.0, "High"),
    MEDIUM(2.0, "Medium"),
    LOW(1.0, "Low")
}
