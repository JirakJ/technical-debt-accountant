package com.jakubjirak.debtaccountant.forecast

import com.jakubjirak.debtaccountant.model.DebtPriority
import java.time.Instant

/**
 * High-level forecasting orchestrator. Given a chronological list of
 * [TimeSeriesPoint]s (as produced by [SnapshotHistoryLoader]), computes
 * trend direction, running averages, and a per-week projection with 95%
 * confidence intervals for a configurable horizon.
 *
 * Week-indexed X axis: the first observed point becomes `x = 0.0` and
 * subsequent points are expressed in weeks elapsed from that origin.
 *
 * @param slopeFlatThreshold absolute slope below which a trend is reported
 *   as [TrendDirection.FLAT]. Expressed in "items per week". Default: 0.25.
 */
class DebtForecaster(
    private val slopeFlatThreshold: Double = 0.25
) {

    /** Main entry point. [horizonWeeks] must be > 0. */
    fun forecast(
        points: List<TimeSeriesPoint>,
        horizonWeeks: Int = 12,
        model: ForecastModel = ForecastModel.AUTO
    ): DebtForecast {
        require(horizonWeeks > 0) { "horizonWeeks must be > 0" }
        if (points.isEmpty()) return DebtForecast.empty(horizonWeeks)

        val sorted = points.sortedBy { it.timestamp }
        val origin = sorted.first().timestamp
        val xs = DoubleArray(sorted.size) { i -> weeksBetween(origin, sorted[i].timestamp) }
        val ysCount = DoubleArray(sorted.size) { i -> sorted[i].count.toDouble() }
        val ysDollars = DoubleArray(sorted.size) { i -> sorted[i].dollars }

        val linearCount = LinearRegression.fit(xs, ysCount)
        val expCount = ExponentialRegression.fit(xs, ysCount)
        val chosen = chooseModel(model, linearCount, expCount)

        val linearDollars = LinearRegression.fit(xs, ysDollars)

        val last = sorted.last()
        val running7 = runningAverage(sorted, last.timestamp, windowDays = 7)
        val running30 = runningAverage(sorted, last.timestamp, windowDays = 30)

        val direction = classify(linearCount.slope)

        val lastX = xs.last()
        val projections = ArrayList<WeeklyProjection>(horizonWeeks)
        for (w in 1..horizonWeeks) {
            val x = lastX + w
            val predictedCount = when (chosen) {
                ForecastModel.LINEAR -> linearCount.predict(x)
                ForecastModel.EXPONENTIAL -> expCount.predict(x)
                ForecastModel.AUTO -> error("AUTO should have been resolved")
            }
            // 95% CI uses ~1.96 * stdErr around the linear fit. For exponential,
            // we widen symmetrically in log-space; here we fall back to linear CI
            // as a conservative approximation — good enough for executive charts.
            val ci = CI95 * linearCount.stdErr
            val predictedDollars = linearDollars.predict(x)
            projections += WeeklyProjection(
                weekIndex = w,
                predictedCount = predictedCount,
                predictedCountLow = predictedCount - ci,
                predictedCountHigh = predictedCount + ci,
                predictedDollars = predictedDollars
            )
        }

        val burndownWeeks = if (linearCount.slope < 0) {
            // count = slope * x + intercept; solve count = 0 → x = -intercept/slope
            -linearCount.intercept / linearCount.slope
        } else Double.NaN
        val burndownDate: Instant? = if (burndownWeeks.isFinite() && burndownWeeks > 0) {
            Instant.ofEpochMilli(origin + (burndownWeeks * WEEK_MILLIS).toLong())
        } else null

        val wowDelta = wowDelta(sorted, last.timestamp)

        val priorityTrends = DebtPriority.entries.associateWith { p ->
            val ys = DoubleArray(sorted.size) { i -> (sorted[i].byPriority[p] ?: 0).toDouble() }
            val fit = LinearRegression.fit(xs, ys)
            PriorityTrend(
                priority = p,
                currentCount = last.byPriority[p] ?: 0,
                slopePerWeek = fit.slope,
                direction = classify(fit.slope)
            )
        }

        return DebtForecast(
            generatedAt = Instant.ofEpochMilli(last.timestamp),
            horizonWeeks = horizonWeeks,
            selectedModel = chosen,
            linearCount = linearCount,
            exponentialCount = expCount,
            linearDollars = linearDollars,
            currentCount = last.count,
            currentDollars = last.dollars,
            runningAvg7dCount = running7,
            runningAvg30dCount = running30,
            direction = direction,
            wowCountDelta = wowDelta,
            burndownDate = burndownDate,
            projections = projections,
            priorityTrends = priorityTrends,
            firstSampleAt = Instant.ofEpochMilli(origin),
            lastSampleAt = Instant.ofEpochMilli(last.timestamp),
            sampleCount = sorted.size
        )
    }

    private fun chooseModel(
        requested: ForecastModel,
        linear: LinearRegressionResult,
        exponential: ExponentialRegressionResult
    ): ForecastModel {
        if (requested != ForecastModel.AUTO) return requested
        val lr2 = if (linear.r2.isFinite()) linear.r2 else Double.NEGATIVE_INFINITY
        val er2 = if (exponential.r2.isFinite()) exponential.r2 else Double.NEGATIVE_INFINITY
        return if (er2 > lr2) ForecastModel.EXPONENTIAL else ForecastModel.LINEAR
    }

    private fun classify(slope: Double): TrendDirection = when {
        !slope.isFinite() -> TrendDirection.FLAT
        slope > slopeFlatThreshold -> TrendDirection.DEGRADING
        slope < -slopeFlatThreshold -> TrendDirection.IMPROVING
        else -> TrendDirection.FLAT
    }

    private fun runningAverage(points: List<TimeSeriesPoint>, anchor: Long, windowDays: Int): Double {
        val windowStart = anchor - windowDays * DAY_MILLIS
        val inWindow = points.filter { it.timestamp in windowStart..anchor }
        if (inWindow.isEmpty()) return Double.NaN
        return inWindow.sumOf { it.count.toDouble() } / inWindow.size
    }

    private fun wowDelta(points: List<TimeSeriesPoint>, anchor: Long): Int? {
        val target = anchor - 7 * DAY_MILLIS
        val prior = points.filter { it.timestamp <= target }.maxByOrNull { it.timestamp } ?: return null
        val current = points.last()
        return current.count - prior.count
    }

    private fun weeksBetween(origin: Long, t: Long): Double = (t - origin).toDouble() / WEEK_MILLIS

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val WEEK_MILLIS = 7L * DAY_MILLIS
        private const val CI95 = 1.96
    }
}

/** Qualitative trend labels reported to the user. */
enum class TrendDirection { IMPROVING, FLAT, DEGRADING }

/** Per-week projection row used by the reporters. */
data class WeeklyProjection(
    val weekIndex: Int,
    val predictedCount: Double,
    val predictedCountLow: Double,
    val predictedCountHigh: Double,
    val predictedDollars: Double
)

/** Trend summary for a single priority bucket. */
data class PriorityTrend(
    val priority: DebtPriority,
    val currentCount: Int,
    val slopePerWeek: Double,
    val direction: TrendDirection
)

/** Complete forecast result. */
data class DebtForecast(
    val generatedAt: Instant,
    val horizonWeeks: Int,
    val selectedModel: ForecastModel,
    val linearCount: LinearRegressionResult,
    val exponentialCount: ExponentialRegressionResult,
    val linearDollars: LinearRegressionResult,
    val currentCount: Int,
    val currentDollars: Double,
    val runningAvg7dCount: Double,
    val runningAvg30dCount: Double,
    val direction: TrendDirection,
    val wowCountDelta: Int?,
    val burndownDate: Instant?,
    val projections: List<WeeklyProjection>,
    val priorityTrends: Map<DebtPriority, PriorityTrend>,
    val firstSampleAt: Instant,
    val lastSampleAt: Instant,
    val sampleCount: Int
) {
    companion object {
        fun empty(horizonWeeks: Int) = DebtForecast(
            generatedAt = Instant.EPOCH,
            horizonWeeks = horizonWeeks,
            selectedModel = ForecastModel.LINEAR,
            linearCount = LinearRegressionResult.EMPTY,
            exponentialCount = ExponentialRegressionResult.empty(0),
            linearDollars = LinearRegressionResult.EMPTY,
            currentCount = 0,
            currentDollars = 0.0,
            runningAvg7dCount = Double.NaN,
            runningAvg30dCount = Double.NaN,
            direction = TrendDirection.FLAT,
            wowCountDelta = null,
            burndownDate = null,
            projections = emptyList(),
            priorityTrends = emptyMap(),
            firstSampleAt = Instant.EPOCH,
            lastSampleAt = Instant.EPOCH,
            sampleCount = 0
        )
    }
}
