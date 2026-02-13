package com.jakubjirak.debtaccountant.model

enum class DebtTrend(val displayName: String, val icon: String) {
    IMPROVING("Improving", "↓"),
    STABLE("Stable", "→"),
    WORSENING("Worsening", "↑")
}
