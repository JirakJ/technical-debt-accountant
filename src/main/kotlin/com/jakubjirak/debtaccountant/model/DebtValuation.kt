package com.jakubjirak.debtaccountant.model

data class DebtValuation(
    val itemId: String,
    val totalWorkaroundCost: Double,
    val estimatedFixCost: Double,
    val monthlyInterest: Double,
    val roiMonths: Double,
    val currency: String = "USD"
)
