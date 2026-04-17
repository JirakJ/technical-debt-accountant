package com.jakubjirak.debtaccountant.digest

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.IsoFields

/**
 * Time window used to aggregate a weekly digest.
 *
 * Implementations compute a deterministic `[startMillis, endMillis)` half-open
 * range in epoch-milliseconds. `endMillis` is exclusive.
 */
sealed class DigestPeriod {

    /** Inclusive start of the window (epoch millis, UTC). */
    abstract fun startMillis(now: Instant): Long

    /** Exclusive end of the window (epoch millis, UTC). */
    abstract fun endMillis(now: Instant): Long

    /** Short human-readable label used in report headers. */
    abstract fun label(now: Instant): String

    /** Rolling window — last 7 days ending at `now`. */
    object Last7 : DigestPeriod() {
        override fun startMillis(now: Instant): Long = now.toEpochMilli() - DAY_MILLIS * 7L
        override fun endMillis(now: Instant): Long = now.toEpochMilli()
        override fun label(now: Instant): String = "Last 7 days"
    }

    /** Rolling window — last 14 days ending at `now`. */
    object Last14 : DigestPeriod() {
        override fun startMillis(now: Instant): Long = now.toEpochMilli() - DAY_MILLIS * 14L
        override fun endMillis(now: Instant): Long = now.toEpochMilli()
        override fun label(now: Instant): String = "Last 14 days"
    }

    /** Rolling window — last 30 days ending at `now`. */
    object Last30 : DigestPeriod() {
        override fun startMillis(now: Instant): Long = now.toEpochMilli() - DAY_MILLIS * 30L
        override fun endMillis(now: Instant): Long = now.toEpochMilli()
        override fun label(now: Instant): String = "Last 30 days"
    }

    /**
     * Specific ISO week identified by `isoYear` (ISO week-based year) and `isoWeek` (1..53).
     * The window begins at Monday 00:00 UTC and ends at the following Monday 00:00 UTC.
     */
    data class Week(val isoYear: Int, val isoWeek: Int) : DigestPeriod() {
        init {
            require(isoWeek in 1..53) { "isoWeek must be in 1..53 (was $isoWeek)" }
        }

        private fun startDate(): LocalDate = LocalDate.now(ZoneOffset.UTC)
            .with(IsoFields.WEEK_BASED_YEAR, isoYear.toLong())
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek.toLong())
            .with(java.time.DayOfWeek.MONDAY)

        override fun startMillis(now: Instant): Long =
            startDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        override fun endMillis(now: Instant): Long =
            startDate().plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        override fun label(now: Instant): String = "ISO week %04d-W%02d".format(isoYear, isoWeek)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        /**
         * Parse a CLI value such as `last7`, `last14`, `last30`, or `week:2026-W15`.
         * Case-insensitive. Returns `null` if the format is not recognized.
         */
        fun parse(value: String): DigestPeriod? {
            val v = value.trim().lowercase()
            return when (v) {
                "last7" -> Last7
                "last14" -> Last14
                "last30" -> Last30
                else -> parseWeek(v)
            }
        }

        private fun parseWeek(v: String): DigestPeriod? {
            if (!v.startsWith("week:")) return null
            val spec = v.removePrefix("week:")
            val m = Regex("""^(\d{4})-w(\d{1,2})$""").matchEntire(spec) ?: return null
            val year = m.groupValues[1].toInt()
            val week = m.groupValues[2].toInt()
            return runCatching { Week(year, week) }.getOrNull()
        }
    }
}
