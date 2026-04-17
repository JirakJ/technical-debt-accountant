package com.jakubjirak.debtaccountant.forecast

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * CLI entry point for the Debt Trend Forecasting feature. Reads persisted
 * digest snapshots from `--snapshots-dir`, fits the chosen model, and writes
 * the forecast to `--out` in Markdown, HTML, or JSON.
 *
 * Usage:
 * ```
 * debt-forecast \
 *   --snapshots-dir=<path>     \
 *   [--horizon-weeks=<n>]      (default 12)
 *   [--model=linear|exponential|auto]  (default auto)
 *   [--format=md|html|json]    (default md)
 *   --out=<path>               \
 *   [--fail-on-degrading]
 * ```
 *
 * Exit codes:
 *  - `0` success, trend is not DEGRADING (or `--fail-on-degrading` not set).
 *  - `1` argument / I/O error, or DEGRADING trend detected with `--fail-on-degrading`.
 */
object DebtForecastCli {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val r = run(args)
        if (r.stdout.isNotEmpty()) print(r.stdout)
        if (r.stderr.isNotEmpty()) System.err.print(r.stderr)
        exitProcess(r.exitCode)
    }

    /** Testable entry point: never calls [exitProcess]. */
    fun run(args: Array<String>): Result {
        var snapshotsDir: String? = null
        var horizonWeeks = 12
        var model = ForecastModel.AUTO
        var format = "md"
        var outPath: String? = null
        var failOnDegrading = false

        for (raw in args) {
            when {
                raw == "--help" || raw == "-h" -> return Result(0, HELP, "")
                raw.startsWith("--snapshots-dir=") -> snapshotsDir = raw.substringAfter('=')
                raw.startsWith("--horizon-weeks=") -> {
                    horizonWeeks = raw.substringAfter('=').toIntOrNull()
                        ?: return Result(1, "", "Invalid --horizon-weeks: ${raw.substringAfter('=')}\n")
                    if (horizonWeeks <= 0) return Result(1, "", "--horizon-weeks must be > 0\n")
                }
                raw.startsWith("--model=") -> {
                    model = when (raw.substringAfter('=').lowercase()) {
                        "linear" -> ForecastModel.LINEAR
                        "exponential" -> ForecastModel.EXPONENTIAL
                        "auto" -> ForecastModel.AUTO
                        else -> return Result(1, "", "Invalid --model: ${raw.substringAfter('=')}\n")
                    }
                }
                raw.startsWith("--format=") -> {
                    format = raw.substringAfter('=').lowercase()
                    if (format !in FORMATS) return Result(1, "", "Invalid --format: $format\n")
                }
                raw.startsWith("--out=") -> outPath = raw.substringAfter('=')
                raw == "--fail-on-degrading" -> failOnDegrading = true
                else -> return Result(1, "", "Unknown argument: $raw\n$HELP")
            }
        }

        if (snapshotsDir == null) return Result(1, "", "Missing --snapshots-dir\n$HELP")
        if (outPath == null) return Result(1, "", "Missing --out\n$HELP")

        val dir: Path = Paths.get(snapshotsDir)
        if (!Files.isDirectory(dir)) return Result(1, "", "Snapshot directory does not exist: $dir\n")

        val points = try {
            SnapshotHistoryLoader(dir).load()
        } catch (e: Exception) {
            return Result(1, "", "Failed to load snapshots: ${e.message}\n")
        }

        val forecast = DebtForecaster().forecast(points, horizonWeeks, model)

        val body = when (format) {
            "md" -> ForecastMarkdownReporter.render(forecast)
            "html" -> ForecastHtmlReporter.render(forecast)
            "json" -> GSON.toJson(forecast)
            else -> return Result(1, "", "Invalid --format: $format\n")
        }

        try {
            val target = Paths.get(outPath)
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, body)
        } catch (e: Exception) {
            return Result(1, "", "Failed to write output: ${e.message}\n")
        }

        val stdout = "Forecast written to $outPath " +
            "(samples=${forecast.sampleCount}, trend=${forecast.direction}, model=${forecast.selectedModel})\n"
        return if (failOnDegrading && forecast.direction == TrendDirection.DEGRADING) {
            Result(1, stdout, "DEGRADING trend detected (--fail-on-degrading)\n")
        } else {
            Result(0, stdout, "")
        }
    }

    private val GSON = GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
    private val FORMATS = setOf("md", "html", "json")

    private const val HELP = """
Usage: debt-forecast --snapshots-dir=<path> --out=<path>
                     [--horizon-weeks=12] [--model=linear|exponential|auto]
                     [--format=md|html|json] [--fail-on-degrading]
"""
}
