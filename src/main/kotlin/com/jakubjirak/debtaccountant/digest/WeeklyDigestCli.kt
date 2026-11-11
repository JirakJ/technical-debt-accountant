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
import com.jakubjirak.debtaccountant.cli.CliAbort
import com.jakubjirak.debtaccountant.cli.cliAttempt
import com.jakubjirak.debtaccountant.cli.cliFail
import com.jakubjirak.debtaccountant.cli.cliHelp
import com.jakubjirak.debtaccountant.cli.cliValue
import com.jakubjirak.debtaccountant.cli.cliWriteFile
import com.jakubjirak.debtaccountant.cli.isHelpArg
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
    fun run(args: Array<String>, nowMillis: Long = System.currentTimeMillis()): Result = try {
        execute(parseArgs(args), nowMillis)
    } catch (abort: CliAbort) {
        Result(abort.exitCode, abort.stdout, abort.stderr)
    }

    private data class Options(
        val period: DigestPeriod,
        val format: String,
        val inventoryPath: String,
        val previousPath: String?,
        val savePath: String?,
        val outPath: String,
        val projectName: String?,
        val hourlyRate: Double,
    )

    private class RawOptions {
        var period: String? = null
        var format = "md"
        var inventoryPath: String? = null
        var previousPath: String? = null
        var savePath: String? = null
        var outPath: String? = null
        var projectName: String? = null
        var hourlyRate = DEFAULT_HOURLY_RATE
    }

    private fun parseArgs(args: Array<String>): Options {
        val raw = RawOptions()
        args.forEach { applyArg(raw, it) }

        val periodStr = raw.period ?: cliFail("Missing --period\n$HELP")
        val inventoryPath = raw.inventoryPath ?: cliFail("Missing --inventory\n$HELP")
        val outPath = raw.outPath ?: cliFail("Missing --out\n$HELP")
        if (raw.format != "md" && raw.format != "html") {
            cliFail("--format must be 'md' or 'html' (was '${raw.format}')\n")
        }
        val period = DigestPeriod.parse(periodStr) ?: cliFail("Invalid --period: $periodStr\n")
        return Options(
            period = period,
            format = raw.format,
            inventoryPath = inventoryPath,
            previousPath = raw.previousPath,
            savePath = raw.savePath,
            outPath = outPath,
            projectName = raw.projectName,
            hourlyRate = raw.hourlyRate,
        )
    }

    private fun applyArg(target: RawOptions, arg: String) {
        when {
            isHelpArg(arg) -> cliHelp(HELP)
            arg.startsWith("--period=") -> target.period = arg.cliValue()
            arg.startsWith("--format=") -> target.format = arg.cliValue().lowercase()
            arg.startsWith("--inventory=") -> target.inventoryPath = arg.cliValue()
            arg.startsWith("--previous-snapshot=") -> target.previousPath = arg.cliValue()
            arg.startsWith("--save-snapshot=") -> target.savePath = arg.cliValue()
            arg.startsWith("--out=") -> target.outPath = arg.cliValue()
            arg.startsWith("--project=") -> target.projectName = arg.cliValue()
            arg.startsWith("--hourly-rate=") ->
                target.hourlyRate = arg.cliValue().toDoubleOrNull() ?: cliFail("Invalid --hourly-rate: ${arg.cliValue()}\n")
            else -> cliFail("Unknown argument: $arg\n$HELP")
        }
    }

    private fun execute(options: Options, nowMillis: Long): Result {
        val items = cliAttempt("Failed to read inventory") { readInventory(Paths.get(options.inventoryPath)) }
        val previous = options.previousPath?.let { path ->
            cliAttempt("Failed to read previous snapshot") {
                DigestSnapshotStore(Paths.get(path).parent ?: Paths.get(".")).load(Paths.get(path))
            }
        }

        val ratePerMinute = options.hourlyRate / MINUTES_PER_HOUR
        val valuator: (TechDebtItem) -> Double = { item ->
            (item.estimatedFixMinutes + item.totalWorkaroundMinutes) * ratePerMinute
        }
        val monthlyValuator: (TechDebtItem) -> Double = { item ->
            item.workaroundLogs
                .filter { log -> log.timestamp >= nowMillis - THIRTY_DAYS_MILLIS }
                .sumOf { it.durationMinutes } * ratePerMinute
        }
        val slaProvider = slaProvider(nowMillis)

        val collector = DigestDataCollector(
            valuator = valuator,
            monthlyInterestValuator = monthlyValuator,
            slaProvider = slaProvider
        )
        val project = options.projectName
            ?: Paths.get(options.inventoryPath).toAbsolutePath().parent?.fileName?.toString()
            ?: "project"
        val data = collector.collect(items, previous, options.period, project, nowMillis)

        val body = if (options.format == "html") HtmlDigestRenderer.render(data) else MarkdownDigestRenderer.render(data)
        cliWriteFile(options.outPath, body)

        options.savePath?.let { savePath ->
            cliAttempt("Failed to save snapshot") {
                val snapshot = DigestSnapshot(
                    takenAtMillis = nowMillis,
                    label = options.period.label(Instant.ofEpochMilli(nowMillis)),
                    items = items.map { TechDebtItemSnapshot.from(it, valuator(it), slaProvider.statusOf(it)) }
                )
                val target = Paths.get(savePath)
                DigestSnapshotStore(target.parent ?: Paths.get(".")).save(snapshot, target.fileName.toString())
            }
        }

        return Result(0, "Digest written to ${options.outPath}\n", "")
    }

    private fun slaProvider(nowMillis: Long) = SlaSummaryProvider { item ->
        val eval = SlaEvaluator.evaluate(listOf(item), SlaPolicy.DEFAULT, Instant.ofEpochMilli(nowMillis))
        when {
            eval.breached.isNotEmpty() -> "BREACHED"
            eval.approaching.isNotEmpty() -> "APPROACHING"
            else -> "OK"
        }
    }

    private fun readInventory(path: Path): List<TechDebtItem> {
        val json = Files.readString(path)
        val type = object : TypeToken<List<TechDebtItem>>() {}.type
        return gson.fromJson<List<TechDebtItem>>(json, type) ?: emptyList()
    }

    private const val DEFAULT_HOURLY_RATE = 80.0
    private const val MINUTES_PER_HOUR = 60.0
    private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

    private const val HELP = """
Usage: weekly-digest --period=last7|last14|last30|week:YYYY-Www --format=md|html --inventory=<path> --out=<path>
                    [--previous-snapshot=<path>] [--save-snapshot=<path>] [--project=<name>] [--hourly-rate=<USD>]
"""
}
