package com.jakubjirak.debtaccountant.scanner

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    fun run(args: Array<String>): Result {
        var root: String? = null
        var inventoryPath: String? = null
        var format = "md"
        var out: String? = null
        var failOver: Int? = null
        for (raw in args) {
            when {
                raw.startsWith("--root=") -> root = raw.substringAfter('=')
                raw.startsWith("--inventory=") -> inventoryPath = raw.substringAfter('=')
                raw.startsWith("--format=") -> format = raw.substringAfter('=')
                raw.startsWith("--out=") -> out = raw.substringAfter('=')
                raw.startsWith("--fail-over=") -> {
                    failOver = raw.substringAfter('=').toIntOrNull()
                        ?: return Result(1, "", "--fail-over must be an integer\n")
                }
                raw == "--help" || raw == "-h" -> return Result(0, HELP, "")
                else -> return Result(1, "", "Unknown argument: $raw\n$HELP")
            }
        }
        if (root == null) return Result(1, "", "Missing required --root=<path>\n$HELP")
        if (format != "md" && format != "json") {
            return Result(1, "", "--format must be 'md' or 'json' (was '$format')\n")
        }
        val rootPath = Paths.get(root)
        if (!Files.isDirectory(rootPath)) return Result(1, "", "Not a directory: $root\n")

        val existing = if (inventoryPath != null) {
            try {
                readInventory(Paths.get(inventoryPath))
            } catch (e: Exception) {
                return Result(1, "", "Failed to read inventory: ${e.message}\n")
            }
        } else {
            emptyList()
        }

        val comments = DebtCommentScanner.dedupe(DebtCommentScanner.scanDirectory(rootPath), existing)
        val body = if (format == "json") renderJson(comments) else renderMarkdown(comments, rootPath)

        if (out != null) {
            try {
                val target = Paths.get(out)
                target.parent?.let { Files.createDirectories(it) }
                Files.writeString(target, body)
            } catch (e: Exception) {
                return Result(1, "", "Failed to write output: ${e.message}\n")
            }
        }

        val exit = if (failOver != null && comments.size > failOver) 2 else 0
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
