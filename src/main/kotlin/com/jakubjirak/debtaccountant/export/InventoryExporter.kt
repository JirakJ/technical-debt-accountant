package com.jakubjirak.debtaccountant.export

import com.google.gson.GsonBuilder
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the debt inventory as CSV (Excel/Sheets-friendly) or JSON, including
 * computed dollar columns so reports work out of the box for management.
 *
 * Pure logic — the hourly rate and currency are passed in, so this is fully
 * unit-testable without the IDE settings service.
 */
object InventoryExporter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = DateTimeFormatter.ISO_INSTANT

    private val CSV_HEADER = listOf(
        "id", "filePath", "lineStart", "lineEnd", "category", "priority", "description",
        "tags", "author", "createdAt", "resolved", "resolvedAt",
        "estimatedFixMinutes", "workaroundMinutes", "workaroundCost", "estimatedFixCost", "currency"
    )

    /** Renders [items] as CSV with a header row. Costs use [hourlyRate] per hour. */
    fun toCsv(items: List<TechDebtItem>, hourlyRate: Double, currency: String): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER.joinToString(",")).append("\r\n")
        items.forEach { item ->
            val row = listOf(
                item.id,
                item.filePath,
                item.lineStart.toString(),
                item.lineEnd.toString(),
                item.category.name,
                item.priority.name,
                item.description,
                item.tags.sorted().joinToString(";"),
                item.author,
                formatTimestamp(item.createdAt),
                item.isResolved.toString(),
                item.resolvedAt?.let { formatTimestamp(it) } ?: "",
                item.estimatedFixMinutes.toString(),
                item.totalWorkaroundMinutes.toString(),
                formatAmount(cost(item.totalWorkaroundMinutes, hourlyRate)),
                formatAmount(cost(item.estimatedFixMinutes, hourlyRate)),
                currency,
            )
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** Renders [items] as pretty-printed JSON with a summary block and per-item costs. */
    fun toJson(items: List<TechDebtItem>, hourlyRate: Double, currency: String): String {
        val payload = mapOf(
            "summary" to mapOf(
                "totalItems" to items.size,
                "activeItems" to items.count { !it.isResolved },
                "resolvedItems" to items.count { it.isResolved },
                "totalWorkaroundMinutes" to items.sumOf { it.totalWorkaroundMinutes },
                "totalWorkaroundCost" to round2(items.sumOf { cost(it.totalWorkaroundMinutes, hourlyRate) }),
                "totalEstimatedFixCost" to round2(items.sumOf { cost(it.estimatedFixMinutes, hourlyRate) }),
                "hourlyRate" to hourlyRate,
                "currency" to currency,
            ),
            "items" to items.map { item ->
                mapOf(
                    "id" to item.id,
                    "filePath" to item.filePath,
                    "lineStart" to item.lineStart,
                    "lineEnd" to item.lineEnd,
                    "category" to item.category.name,
                    "priority" to item.priority.name,
                    "description" to item.description,
                    "tags" to item.tags.sorted(),
                    "author" to item.author,
                    "createdAt" to formatTimestamp(item.createdAt),
                    "resolved" to item.isResolved,
                    "resolvedAt" to item.resolvedAt?.let { formatTimestamp(it) },
                    "estimatedFixMinutes" to item.estimatedFixMinutes,
                    "workaroundMinutes" to item.totalWorkaroundMinutes,
                    "workaroundCost" to round2(cost(item.totalWorkaroundMinutes, hourlyRate)),
                    "estimatedFixCost" to round2(cost(item.estimatedFixMinutes, hourlyRate)),
                )
            },
        )
        return gson.toJson(payload) + "\n"
    }

    /**
     * Escapes one CSV field per RFC 4180: fields containing a comma, quote, or
     * line break are wrapped in quotes with inner quotes doubled.
     */
    fun escapeCsv(field: String): String =
        if (field.any { it in ",\"\n\r" }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private fun cost(minutes: Int, hourlyRate: Double): Double = minutes * (hourlyRate / 60.0)

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatTimestamp(epochMillis: Long): String =
        dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC))
}
