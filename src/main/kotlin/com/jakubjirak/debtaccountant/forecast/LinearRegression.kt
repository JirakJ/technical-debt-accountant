package com.jakubjirak.debtaccountant.forecast

import kotlin.math.sqrt

/**
 * Ordinary Least Squares (OLS) simple linear regression for `y = slope * x + intercept`.
 *
 * Formulas:
 * - `slope = Σ((x_i - x̄)(y_i - ȳ)) / Σ((x_i - x̄)²)`
 * - `intercept = ȳ - slope * x̄`
 * - `SSR = Σ(ŷ_i - ȳ)²`, `SST = Σ(y_i - ȳ)²`
 * - `r² = 1 - SSE/SST` where `SSE = Σ(y_i - ŷ_i)²`
 * - `stdErr = sqrt(SSE / (n - 2))` — residual standard error of the regression
 *
 * Behaviour for degenerate input:
 * - `n < 2`  → [LinearRegressionResult.EMPTY] (all NaN, `n = input.size`).
 * - All `x` identical (no variance in x) → slope = NaN, intercept = NaN.
 * - All `y` identical (perfect flat line) → slope = 0, r² = 1, stdErr = 0.
 */
object LinearRegression {

    /**
     * Fit a line to the (x, y) pairs. [x] and [y] must be the same length.
     *
     * @throws IllegalArgumentException if the arrays have different lengths.
     */
    fun fit(x: DoubleArray, y: DoubleArray): LinearRegressionResult {
        require(x.size == y.size) { "x and y must have the same length (was ${x.size} vs ${y.size})" }
        val n = x.size
        if (n < 2) return LinearRegressionResult.empty(n)

        var sumX = 0.0
        var sumY = 0.0
        for (i in 0 until n) {
            sumX += x[i]
            sumY += y[i]
        }
        val meanX = sumX / n
        val meanY = sumY / n

        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }

        if (sxx == 0.0) {
            // No variance in x — slope undefined.
            return LinearRegressionResult(
                slope = Double.NaN,
                intercept = Double.NaN,
                r2 = Double.NaN,
                stdErr = Double.NaN,
                n = n
            )
        }

        val slope = sxy / sxx
        val intercept = meanY - slope * meanX

        var sse = 0.0
        for (i in 0 until n) {
            val predicted = slope * x[i] + intercept
            val residual = y[i] - predicted
            sse += residual * residual
        }

        val r2 = if (syy == 0.0) 1.0 else 1.0 - sse / syy
        val stdErr = if (n > 2) sqrt(sse / (n - 2)) else 0.0

        return LinearRegressionResult(slope, intercept, r2, stdErr, n)
    }
}

/**
 * Result of a [LinearRegression.fit] call. All values may be `NaN` for
 * degenerate inputs (see [LinearRegression] KDoc).
 */
data class LinearRegressionResult(
    val slope: Double,
    val intercept: Double,
    val r2: Double,
    val stdErr: Double,
    val n: Int
) {
    /** Predict `y` at [xValue] using the fitted line. */
    fun predict(xValue: Double): Double = slope * xValue + intercept

    companion object {
        val EMPTY = LinearRegressionResult(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0)
        fun empty(n: Int) = LinearRegressionResult(Double.NaN, Double.NaN, Double.NaN, Double.NaN, n)
    }
}
