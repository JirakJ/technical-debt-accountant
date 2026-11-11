package com.jakubjirak.debtaccountant.scanner

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jakubjirak.debtaccountant.model.TechDebtItem
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
 * Command-line interface for the debt comment scanner, usable from CI pipelines.
 *
 * Usage:
 * ```
 * scan --root=<path> [--inventory=<path>] [--format=md|json] [--out=<path>] [--fail-over=<n>]
 * ```
 * `--inventory` deduplicates results against an existing `.tech-debt/inventory.json`.
 *
 * Exit codes:
 *  - `0` — scan completed, marker count within tolerance.
 *  - `1` — bad arguments or unreadable input.
 *  - `2` — more than `--fail-over` new markers found.
 */
object DebtScanCli {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Result of running the CLI. Returned by [run] so tests can avoid `exitProcess`. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    /** Entry point used by packaging. Calls [exitProcess]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = run(args)
        if (result.stdout.isNotEmpty()) print(result.stdout)
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        exitProcess(result.exitCode)
    }

    /** Testable entry point: parses args, runs the scan, returns a [Result]. */
    fun run(args: Array<String>): Result = try {
        execute(parseArgs(args))
    } catch (abort: CliAbort) {
        Result(abort.exitCode, abort.stdout, abort.stderr)
    }

    private data class Options(
        val root: String,
        val inventoryPath: String?,
        val format: String,
        val out: String?,
        val failOver: Int?,
    )

    private fun parseArgs(args: Array<String>): Options {
        var root: String? = null
        var inventoryPath: String? = null
        var format = "md"
        var out: String? = null
        var failOver: Int? = null
        for (raw in args) {
            when {
                raw.startsWith("--root=") -> root = raw.cliValue()
                raw.startsWith("--inventory=") -> inventoryPath = raw.cliValue()
                raw.startsWith("--format=") -> format = raw.cliValue()
                raw.startsWith("--out=") -> out = raw.cliValue()
                raw.startsWith("--fail-over=") ->
                    failOver = raw.cliValue().toIntOrNull() ?: cliFail("--fail-over must be an integer\n")
                isHelpArg(raw) -> cliHelp(HELP)
                else -> cliFail("Unknown argument: $raw\n$HELP")
            }
        }
        if (root == null) cliFail("Missing required --root=<path>\n$HELP")
        if (format != "md" && format != "json") cliFail("--format must be 'md' or 'json' (was '$format')\n")
        return Options(root, inventoryPath, format, out, failOver)
    }

    private fun execute(options: Options): Result {
        val rootPath = Paths.get(options.root)
        if (!Files.isDirectory(rootPath)) cliFail("Not a directory: ${options.root}\n")

        val existing = options.inventoryPath
            ?.let { path -> cliAttempt("Failed to read inventory") { readInventory(Paths.get(path)) } }
            .orEmpty()

        val comments = DebtCommentScanner.dedupe(DebtCommentScanner.scanDirectory(rootPath), existing)
        val body = if (options.format == "json") renderJson(comments) else renderMarkdown(comments, rootPath)

        options.out?.let { cliWriteFile(it, body) }

        val exit = if (options.failOver != null && comments.size > options.failOver) 2 else 0
        return Result(exit, body, "")
    }

    private fun readInventory(path: Path): List<TechDebtItem> {
        val json = Files.readString(path)
        val type = object : TypeToken<List<TechDebtItem>>() {}.type
        return gson.fromJson<List<TechDebtItem>>(json, type) ?: emptyList()
    }

    private fun renderMarkdown(comments: List<ScannedComment>, root: Path): String {
        val sb = StringBuilder()
        sb.appendLine("# Debt Comment Scan — ${root.toAbsolutePath().fileName}")
        sb.appendLine()
        sb.appendLine("Found **${comments.size}** debt comment(s).")
        sb.appendLine()
        if (comments.isNotEmpty()) {
            sb.appendLine("| Marker | Location | Comment |")
            sb.appendLine("|---|---|---|")
            comments.forEach { c ->
                val text = c.text.replace("|", "\\|").ifEmpty { "—" }
                sb.appendLine("| ${c.marker.name} | `${c.filePath}:${c.line}` | $text |")
            }
            sb.appendLine()
            val byMarker = comments.groupingBy { it.marker }.eachCount()
            sb.appendLine("## Breakdown")
            sb.appendLine()
            DebtMarker.entries.forEach { marker ->
                val count = byMarker[marker] ?: return@forEach
                sb.appendLine("- ${marker.name}: $count")
            }
        }
        return sb.toString()
    }

    private fun renderJson(comments: List<ScannedComment>): String {
        val payload = mapOf(
            "total" to comments.size,
            "comments" to comments.map {
                mapOf(
                    "marker" to it.marker.name,
                    "filePath" to it.filePath,
                    "line" to it.line,
                    "text" to it.text,
                    "category" to it.marker.category.name,
                    "priority" to it.marker.priority.name,
                )
            },
        )
        return gson.toJson(payload) + "\n"
    }

    private const val HELP = """
Usage: scan --root=<path> [--inventory=<path>] [--format=md|json] [--out=<path>] [--fail-over=<n>]
"""
}
