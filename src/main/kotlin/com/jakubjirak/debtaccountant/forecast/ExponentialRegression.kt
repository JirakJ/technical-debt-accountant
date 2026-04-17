package com.jakubjirak.debtaccountant.forecast

import kotlin.math.exp
import kotlin.math.ln

/**
 * Log-linear exponential regression: fits `y = a * exp(b * x)` by transforming
 * `y' = ln(y)` and running [LinearRegression] on `(x, y')`. The fitted
 * parameters are then `a = exp(intercept)`, `b = slope`.
 *
 * Points with non-positive `y` are dropped prior to the log transform — the
 * exponential model is undefined for `y ≤ 0`. If fewer than 2 usable points
 * remain, [ExponentialRegressionResult.empty] is returned.
 */
object ExponentialRegression {

    fun fit(x: DoubleArray, y: DoubleArray): ExponentialRegressionResult {
        require(x.size == y.size) { "x and y must have the same length (was ${x.size} vs ${y.size})" }
        val xs = ArrayList<Double>(x.size)
        val logs = ArrayList<Double>(y.size)
        for (i in x.indices) {
            if (y[i] > 0.0) {
                xs.add(x[i])
                logs.add(ln(y[i]))
            }
        }
        if (xs.size < 2) return ExponentialRegressionResult.empty(xs.size)

        val linear = LinearRegression.fit(xs.toDoubleArray(), logs.toDoubleArray())
        if (linear.slope.isNaN() || linear.intercept.isNaN()) {
            return ExponentialRegressionResult.empty(xs.size)
        }
        return ExponentialRegressionResult(
            a = exp(linear.intercept),
            b = linear.slope,
            r2 = linear.r2,
            n = xs.size
        )
    }
}

/** Fitted parameters for `y = a * exp(b * x)`. */
data class ExponentialRegressionResult(
    val a: Double,
    val b: Double,
    val r2: Double,
    val n: Int
) {
    /** Predict `y` at [xValue] using the fitted curve. */
    fun predict(xValue: Double): Double = a * exp(b * xValue)

    companion object {
        fun empty(n: Int) = ExponentialRegressionResult(Double.NaN, Double.NaN, Double.NaN, n)
    }
}
