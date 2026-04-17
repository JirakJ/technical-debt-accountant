package com.jakubjirak.debtaccountant.forecast

/**
 * Which regression family [DebtForecaster] should use when projecting
 * future values.
 *
 * - [LINEAR]      — always use OLS linear regression.
 * - [EXPONENTIAL] — always use log-linear exponential regression.
 * - [AUTO]        — fit both and pick the one with the higher `r²`
 *   (linear wins ties, so it is preferred when both fit equally well).
 */
enum class ForecastModel { LINEAR, EXPONENTIAL, AUTO }
