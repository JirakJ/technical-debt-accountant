package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem

/** Top file (by $ value) surfaced in the digest. */
data class DigestHotspot(
    val filePath: String,
    val itemCount: Int,
    val totalDollarValue: Double,
    val highestPriority: DebtPriority
)

/** Single ROI-ranked recommendation surfaced in the digest. */
data class DigestRecommendation(
    val itemId: String,
    val description: String,
    val priority: DebtPriority,
    val fixEffortHours: Double,
    val monthlyDollarSaved: Double,
    val roi: Double
)

/** SLA-derived counters for the Compliance section. */
data class DigestSlaSummary(
    val ok: Int,
    val approaching: Int,
    val breached: Int,
    val worstBreachDays: Long
) {
    /** Total of OK + APPROACHING + BREACHED (excludes resolved items). */
    val total: Int get() = ok + approaching + breached
}

/**
 * Week-over-week delta vs a previous [DigestSnapshot]. Deltas are `current - previous`,
 * so positive values mean the number grew during the window.
 */
data class DigestDelta(
    val previousLabel: String,
    val itemCountDelta: Int,
    val dollarValueDelta: Double,
    val priorityMixDelta: Map<DebtPriority, Int>,
    val newlyResolvedSincePrevious: Int
)

/**
 * Fully-assembled data structure rendered by [MarkdownDigestRenderer] and
 * [HtmlDigestRenderer]. Immutable and deterministic given the same inputs.
 */
data class DigestData(
    val projectName: String,
    val period: DigestPeriod,
    val periodLabel: String,
    val generatedAtMillis: Long,
    val totalItemCount: Int,
    val priorityBreakdown: Map<DebtPriority, Int>,
    val totalDollarValue: Double,
    val newInPeriod: Int,
    val resolvedInPeriod: Int,
    val hotspots: List<DigestHotspot>,
    val sla: DigestSlaSummary,
    val recommendations: List<DigestRecommendation>,
    val delta: DigestDelta?
)

/**
 * Minimal hooks the collector needs from the SLA engine. Kept as an interface
 * so tests (and the CLI) can plug in either the real `SlaEvaluator` or a stub
 * without constructing an IntelliJ `Project`.
 */
fun interface SlaSummaryProvider {
    /** Classify the SLA status of [item] as of the `now` baseline used by the collector. */
    fun statusOf(item: TechDebtItem): String
}

/**
 * Assembles a [DigestData] object from a current inventory plus an optional
 * prior snapshot. This class does no rendering — it just computes.
 *
 * @property valuator maps a [TechDebtItem] to its dollar value for the digest.
 *   Production callers wire `DollarValuator().valuate(it).estimatedFixCost`;
 *   the CLI uses a fixed-rate approximation based on `--hourly-rate`.
 * @property monthlyInterestValuator maps a [TechDebtItem] to its monthly dollar
 *   cost (used to rank ROI recommendations).
 * @property slaProvider classifier for SLA status of each item (OK/APPROACHING/BREACHED).
 * @property topHotspots cap for the hotspots table. Defaults to 5.
 * @property topRecommendations cap for the ROI table. Defaults to 5.
 */
class DigestDataCollector(
    private val valuator: (TechDebtItem) -> Double,
    private val monthlyInterestValuator: (TechDebtItem) -> Double = { 0.0 },
    private val slaProvider: SlaSummaryProvider = SlaSummaryProvider { "UNKNOWN" },
    private val topHotspots: Int = 5,
    private val topRecommendations: Int = 5
) {

    /**
     * Aggregate [items] for [period] and return the full digest payload.
     *
     * @param items the current inventory (resolved items are counted separately
     *   but excluded from the hotspots / recommendation tables).
     * @param previous the previous snapshot used for WoW delta calculation, or `null`.
     * @param projectName name shown in report headers.
     * @param nowMillis baseline "now" timestamp, defaulted to the system clock.
     */
    fun collect(
        items: List<TechDebtItem>,
        previous: DigestSnapshot?,
        period: DigestPeriod,
        projectName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DigestData {
        val now = java.time.Instant.ofEpochMilli(nowMillis)
        val start = period.startMillis(now)
        val end = period.endMillis(now)

        val active = items.filter { !it.isResolved }
        val priorityBreakdown = DebtPriority.values().associateWith { p -> active.count { it.priority == p } }
        val totalDollar = active.sumOf(valuator)

        val newInPeriod = items.count { it.createdAt in start until end }
        val resolvedInPeriod = items.count { it.isResolved && (it.resolvedAt ?: it.updatedAt) in start until end }

        val hotspots = active.groupBy { it.filePath }
            .map { (file, group) ->
                DigestHotspot(
                    filePath = file,
                    itemCount = group.size,
                    totalDollarValue = group.sumOf(valuator),
                    highestPriority = group.maxByOrNull { it.priority.weight }!!.priority
                )
            }
            .sortedWith(compareByDescending<DigestHotspot> { it.totalDollarValue }.thenByDescending { it.itemCount }.thenBy { it.filePath })
            .take(topHotspots)

        val recommendations = active
            .filter { it.estimatedFixMinutes > 0 }
            .map { item ->
                val hours = item.estimatedFixMinutes / 60.0
                val monthly = monthlyInterestValuator(item)
                DigestRecommendation(
                    itemId = item.id,
                    description = item.description,
                    priority = item.priority,
                    fixEffortHours = hours,
                    monthlyDollarSaved = monthly,
                    roi = if (hours > 0) monthly / hours else 0.0
                )
            }
            .sortedWith(
                compareByDescending<DigestRecommendation> { it.roi }
                    .thenByDescending { it.priority.weight }
                    .thenBy { it.itemId }
            )
            .take(topRecommendations)

        val sla = computeSlaSummary(active)

        val delta = previous?.let { buildDelta(active, priorityBreakdown, totalDollar, it) }

        return DigestData(
            projectName = projectName,
            period = period,
            periodLabel = period.label(now),
            generatedAtMillis = nowMillis,
            totalItemCount = active.size,
            priorityBreakdown = priorityBreakdown,
            totalDollarValue = totalDollar,
            newInPeriod = newInPeriod,
            resolvedInPeriod = resolvedInPeriod,
            hotspots = hotspots,
            sla = sla,
            recommendations = recommendations,
            delta = delta
        )
    }

    private fun computeSlaSummary(active: List<TechDebtItem>): DigestSlaSummary {
        var ok = 0
        var approaching = 0
        var breached = 0
        for (it in active) {
            when (slaProvider.statusOf(it)) {
                "OK" -> ok++
                "APPROACHING" -> approaching++
                "BREACHED" -> breached++
                else -> ok++
            }
        }
        val worst = active
            .filter { slaProvider.statusOf(it) == "BREACHED" }
            .maxOfOrNull { (System.currentTimeMillis() - it.createdAt) / DAY_MILLIS } ?: 0L
        return DigestSlaSummary(ok = ok, approaching = approaching, breached = breached, worstBreachDays = worst)
    }

    private fun buildDelta(
        current: List<TechDebtItem>,
        priorityBreakdown: Map<DebtPriority, Int>,
        totalDollar: Double,
        previous: DigestSnapshot
    ): DigestDelta {
        val prevActive = previous.items.filter { !it.resolved }
        val prevDollar = prevActive.sumOf { it.dollarValue }
        val prevMix = DebtPriority.values().associateWith { p -> prevActive.count { it.priority == p } }
        val mixDelta = DebtPriority.values()
            .associateWith { p -> (priorityBreakdown[p] ?: 0) - (prevMix[p] ?: 0) }
        val prevIds = prevActive.map { it.id }.toSet()
        val currentIds = current.map { it.id }.toSet()
        val newlyResolved = prevIds.count { it !in currentIds } +
            current.count { it.isResolved && it.id in prevIds }
        return DigestDelta(
            previousLabel = previous.label,
            itemCountDelta = current.size - prevActive.size,
            dollarValueDelta = totalDollar - prevDollar,
            priorityMixDelta = mixDelta,
            newlyResolvedSincePrevious = newlyResolved
        )
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}
