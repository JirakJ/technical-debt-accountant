package com.jakubjirak.debtaccountant.digest

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.sla.SlaEvaluator
import com.jakubjirak.debtaccountant.sla.SlaPolicy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Command-line entry point that produces a Weekly Digest report. Designed to
 * be called from CI pipelines with a pre-exported inventory JSON.
 *
 * Usage:
 * ```
 * weekly-digest \
 *   --period=last7|last14|last30|week:YYYY-Www \
 *   --format=md|html \
 *   --inventory=<path-to-inventory.json> \
 *   [--previous-snapshot=<path>] \
 *   [--save-snapshot=<path>] \
 *   [--project=<name>] \
 *   [--hourly-rate=<USD>] \
 *   --out=<path>
 * ```
 * Exit codes:
 *  - `0` — digest generated successfully.
 *  - `1` — argument error or I/O failure.
 */
object WeeklyDigestCli {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Parsed and validated CLI arguments plus a final [Result] value. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    /** Shell entry point. Prints [Result.stdout]/[Result.stderr] and exits. */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = run(args)
        if (result.stdout.isNotEmpty()) print(result.stdout)
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        exitProcess(result.exitCode)
    }

    /** Testable entry point: no [exitProcess]. */
    fun run(args: Array<String>, nowMillis: Long = System.currentTimeMillis()): Result {
        var periodStr: String? = null
        var format = "md"
        var inventoryPath: String? = null
        var previousPath: String? = null
        var savePath: String? = null
        var outPath: String? = null
        var projectName: String? = null
        var hourlyRate = DEFAULT_HOURLY_RATE

        for (raw in args) {
            when {
                raw == "--help" || raw == "-h" -> return Result(0, HELP, "")
                raw.startsWith("--period=") -> periodStr = raw.substringAfter('=')
                raw.startsWith("--format=") -> format = raw.substringAfter('=').lowercase()
                raw.startsWith("--inventory=") -> inventoryPath = raw.substringAfter('=')
                raw.startsWith("--previous-snapshot=") -> previousPath = raw.substringAfter('=')
                raw.startsWith("--save-snapshot=") -> savePath = raw.substringAfter('=')
                raw.startsWith("--out=") -> outPath = raw.substringAfter('=')
                raw.startsWith("--project=") -> projectName = raw.substringAfter('=')
                raw.startsWith("--hourly-rate=") -> {
                    hourlyRate = raw.substringAfter('=').toDoubleOrNull()
                        ?: return Result(1, "", "Invalid --hourly-rate: ${raw.substringAfter('=')}\n")
                }
                else -> return Result(1, "", "Unknown argument: $raw\n$HELP")
            }
        }

        if (periodStr == null) return Result(1, "", "Missing --period\n$HELP")
        if (inventoryPath == null) return Result(1, "", "Missing --inventory\n$HELP")
        if (outPath == null) return Result(1, "", "Missing --out\n$HELP")
        if (format != "md" && format != "html") {
            return Result(1, "", "--format must be 'md' or 'html' (was '$format')\n")
        }
        val period = DigestPeriod.parse(periodStr)
            ?: return Result(1, "", "Invalid --period: $periodStr\n")

        val items = try {
            readInventory(Paths.get(inventoryPath))
        } catch (e: Exception) {
            return Result(1, "", "Failed to read inventory: ${e.message}\n")
        }

        val previous = previousPath?.let {
            try {
                DigestSnapshotStore(Paths.get(it).parent ?: Paths.get(".")).load(Paths.get(it))
            } catch (e: Exception) {
                return Result(1, "", "Failed to read previous snapshot: ${e.message}\n")
            }
        }

        val valuator: (TechDebtItem) -> Double = { item ->
            (item.estimatedFixMinutes + item.totalWorkaroundMinutes) * (hourlyRate / 60.0)
        }
        val monthlyValuator: (TechDebtItem) -> Double = { item ->
            val recent = item.workaroundLogs
                .filter { log -> log.timestamp >= nowMillis - THIRTY_DAYS_MILLIS }
                .sumOf { it.durationMinutes }
            recent * (hourlyRate / 60.0)
        }
        val slaProvider = SlaSummaryProvider { item ->
            val eval = SlaEvaluator.evaluate(listOf(item), SlaPolicy.DEFAULT, Instant.ofEpochMilli(nowMillis))
            when {
                eval.breached.isNotEmpty() -> "BREACHED"
                eval.approaching.isNotEmpty() -> "APPROACHING"
                else -> "OK"
            }
        }

        val collector = DigestDataCollector(
            valuator = valuator,
            monthlyInterestValuator = monthlyValuator,
            slaProvider = slaProvider
        )
        val project = projectName ?: Paths.get(inventoryPath).toAbsolutePath().parent?.fileName?.toString() ?: "project"
        val data = collector.collect(items, previous, period, project, nowMillis)

        val body = if (format == "html") HtmlDigestRenderer.render(data) else MarkdownDigestRenderer.render(data)
        try {
            val target = Paths.get(outPath)
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, body)
        } catch (e: Exception) {
            return Result(1, "", "Failed to write output: ${e.message}\n")
        }

        if (savePath != null) {
            try {
                val snapshot = DigestSnapshot(
                    takenAtMillis = nowMillis,
                    label = period.label(Instant.ofEpochMilli(nowMillis)),
                    items = items.map { TechDebtItemSnapshot.from(it, valuator(it), slaProvider.statusOf(it)) }
                )
                val target = Paths.get(savePath)
                val store = DigestSnapshotStore(target.parent ?: Paths.get("."))
                store.save(snapshot, target.fileName.toString())
            } catch (e: Exception) {
                return Result(1, "", "Failed to save snapshot: ${e.message}\n")
            }
        }

        return Result(0, "Digest written to $outPath\n", "")
    }

    private fun readInventory(path: Path): List<TechDebtItem> {
        val json = Files.readString(path)
        val type = object : TypeToken<List<TechDebtItem>>() {}.type
        return gson.fromJson<List<TechDebtItem>>(json, type) ?: emptyList()
    }

    private const val DEFAULT_HOURLY_RATE = 80.0
    private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

    private const val HELP = """
Usage: weekly-digest --period=last7|last14|last30|week:YYYY-Www --format=md|html --inventory=<path> --out=<path>
                    [--previous-snapshot=<path>] [--save-snapshot=<path>] [--project=<name>] [--hourly-rate=<USD>]
"""
}
