package com.jakubjirak.debtaccountant.forecast

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.jakubjirak.debtaccountant.cli.CliAbort
import com.jakubjirak.debtaccountant.cli.cliAttempt
import com.jakubjirak.debtaccountant.cli.cliFail
import com.jakubjirak.debtaccountant.cli.cliHelp
import com.jakubjirak.debtaccountant.cli.cliValue
import com.jakubjirak.debtaccountant.cli.cliWriteFile
import com.jakubjirak.debtaccountant.cli.isHelpArg
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
    fun run(args: Array<String>): Result = try {
        execute(parseArgs(args))
    } catch (abort: CliAbort) {
        Result(abort.exitCode, abort.stdout, abort.stderr)
    }

    private data class Options(
        val snapshotsDir: String,
        val horizonWeeks: Int,
        val model: ForecastModel,
        val format: String,
        val outPath: String,
        val failOnDegrading: Boolean,
    )

    private fun parseArgs(args: Array<String>): Options {
        var snapshotsDir: String? = null
        var horizonWeeks = DEFAULT_HORIZON_WEEKS
        var model = ForecastModel.AUTO
        var format = "md"
        var outPath: String? = null
        var failOnDegrading = false

        for (raw in args) {
            when {
                isHelpArg(raw) -> cliHelp(HELP)
                raw.startsWith("--snapshots-dir=") -> snapshotsDir = raw.cliValue()
                raw.startsWith("--horizon-weeks=") -> horizonWeeks = parseHorizon(raw.cliValue())
                raw.startsWith("--model=") -> model = parseModel(raw.cliValue())
                raw.startsWith("--format=") -> format = parseFormat(raw.cliValue())
                raw.startsWith("--out=") -> outPath = raw.cliValue()
                raw == "--fail-on-degrading" -> failOnDegrading = true
                else -> cliFail("Unknown argument: $raw\n$HELP")
            }
        }

        return Options(
            snapshotsDir = snapshotsDir ?: cliFail("Missing --snapshots-dir\n$HELP"),
            horizonWeeks = horizonWeeks,
            model = model,
            format = format,
            outPath = outPath ?: cliFail("Missing --out\n$HELP"),
            failOnDegrading = failOnDegrading,
        )
    }

    private fun parseHorizon(value: String): Int {
        val weeks = value.toIntOrNull() ?: cliFail("Invalid --horizon-weeks: $value\n")
        if (weeks <= 0) cliFail("--horizon-weeks must be > 0\n")
        return weeks
    }

    private fun parseModel(value: String): ForecastModel = when (value.lowercase()) {
        "linear" -> ForecastModel.LINEAR
        "exponential" -> ForecastModel.EXPONENTIAL
        "auto" -> ForecastModel.AUTO
        else -> cliFail("Invalid --model: $value\n")
    }

    private fun parseFormat(value: String): String {
        val format = value.lowercase()
        if (format !in FORMATS) cliFail("Invalid --format: $format\n")
        return format
    }

    private fun execute(options: Options): Result {
        val dir: Path = Paths.get(options.snapshotsDir)
        if (!Files.isDirectory(dir)) cliFail("Snapshot directory does not exist: $dir\n")

        val points = cliAttempt("Failed to load snapshots") { SnapshotHistoryLoader(dir).load() }
        val forecast = DebtForecaster().forecast(points, options.horizonWeeks, options.model)

        val body = when (options.format) {
            "html" -> ForecastHtmlReporter.render(forecast)
            "json" -> GSON.toJson(forecast)
            else -> ForecastMarkdownReporter.render(forecast)
        }
        cliWriteFile(options.outPath, body)

        val stdout = "Forecast written to ${options.outPath} " +
            "(samples=${forecast.sampleCount}, trend=${forecast.direction}, model=${forecast.selectedModel})\n"
        return if (options.failOnDegrading && forecast.direction == TrendDirection.DEGRADING) {
            Result(1, stdout, "DEGRADING trend detected (--fail-on-degrading)\n")
        } else {
            Result(0, stdout, "")
        }
    }

    private const val DEFAULT_HORIZON_WEEKS = 12

    private val GSON = GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
    private val FORMATS = setOf("md", "html", "json")

    private const val HELP = """
Usage: debt-forecast --snapshots-dir=<path> --out=<path>
                     [--horizon-weeks=12] [--model=linear|exponential|auto]
                     [--format=md|html|json] [--fail-on-degrading]
"""
}
