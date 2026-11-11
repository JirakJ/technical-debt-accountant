package com.jakubjirak.debtaccountant.settings

/** Parsing/validation of values typed into the settings page. Pure logic, unit-tested. */
object SettingsInput {
    const val DEFAULT_HOURLY_RATE = 80.0
    const val DEFAULT_CURRENCY = "USD"
    val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "GBP", "CZK")

    /** Parses a user-entered hourly rate; blank, invalid or non-positive input falls back to the default. */
    fun parseHourlyRate(text: String): Double =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_HOURLY_RATE

    /** Parses a user-entered monthly budget; invalid input means "no budget" (0), negatives clamp to 0. */
    fun parseBudget(text: String): Double =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
}
