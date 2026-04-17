package com.jakubjirak.debtaccountant.sla

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Command-line interface for the SLA engine, usable from CI pipelines.
 *
 * Usage:
 * ```
 * sla --inventory=<path> [--policy=<path>] [--output=md|json] [--fail-on-breach]
 * ```
 * Exit codes:
 *  - `0` — evaluation completed with no breaches, or breaches tolerated.
 *  - `1` — bad arguments or unreadable input.
 *  - `2` — breaches ≥ 1 when `--fail-on-breach` is set.
 */
object SlaCli {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Result of running the CLI. Returned by [run] so tests can avoid `exitProcess`. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    /** Entry point used by `./gradlew run` or packaging. Calls [exitProcess]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = run(args)
        if (result.stdout.isNotEmpty()) print(result.stdout)
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        exitProcess(result.exitCode)
    }

    /** Testable entry point: parses args, runs evaluation, returns a [Result]. */
    fun run(args: Array<String>, now: Instant = Instant.now()): Result {
        var inventoryPath: String? = null
        var policyPath: String? = null
        var output = "md"
        var failOnBreach = false
        for (raw in args) {
            when {
                raw.startsWith("--inventory=") -> inventoryPath = raw.substringAfter('=')
                raw.startsWith("--policy=") -> policyPath = raw.substringAfter('=')
                raw.startsWith("--output=") -> output = raw.substringAfter('=')
                raw == "--fail-on-breach" -> failOnBreach = true
                raw == "--help" || raw == "-h" -> return Result(0, HELP, "")
                else -> return Result(1, "", "Unknown argument: $raw\n$HELP")
            }
        }
        if (inventoryPath == null) {
            return Result(1, "", "Missing required --inventory=<path>\n$HELP")
        }
        if (output != "md" && output != "json") {
            return Result(1, "", "--output must be 'md' or 'json' (was '$output')\n")
        }

        val items = try {
            readInventory(Paths.get(inventoryPath))
        } catch (e: Exception) {
            return Result(1, "", "Failed to read inventory: ${e.message}\n")
        }

        val policy = if (policyPath != null) {
            val p = Paths.get(policyPath)
            if (!Files.exists(p)) return Result(1, "", "Policy file not found: $policyPath\n")
            SlaPolicyStore.fromJson(Files.readString(p))
        } else {
            SlaPolicy.DEFAULT
        }

        val policyErrors = policy.validate()
        if (policyErrors.isNotEmpty()) {
            return Result(1, "", "Invalid policy:\n" + policyErrors.joinToString("\n") { "  - $it" } + "\n")
        }

        val eval = SlaEvaluator.evaluate(items, policy, now)
        val body = if (output == "json") renderJson(eval) else SlaMarkdownReporter.render(eval, projectNameFromPath(inventoryPath))
        val exit = if (failOnBreach && eval.summary.breachedCount > 0) 2 else 0
        return Result(exit, body, "")
    }

    private fun readInventory(path: Path): List<TechDebtItem> {
        val json = Files.readString(path)
        val type = object : TypeToken<List<TechDebtItem>>() {}.type
        return gson.fromJson<List<TechDebtItem>>(json, type) ?: emptyList()
    }

    private fun renderJson(eval: SlaEvaluation): String {
        val payload = mapOf(
            "summary" to eval.summary,
            "breached" to eval.breached.map {
                mapOf("id" to it.item.id, "ageDays" to it.ageDays, "daysOver" to it.daysOver, "priority" to it.item.priority.name)
            },
            "approaching" to eval.approaching.map {
                mapOf("id" to it.item.id, "ageDays" to it.ageDays, "daysToBreach" to it.daysToBreach, "priority" to it.item.priority.name)
            },
            "escalations" to eval.escalations.map {
                mapOf("id" to it.item.id, "from" to it.from.name, "to" to it.to.name)
            },
        )
        return gson.toJson(payload) + "\n"
    }

    private fun projectNameFromPath(p: String): String =
        Paths.get(p).toAbsolutePath().parent?.fileName?.toString() ?: "project"

    private const val HELP = """
Usage: sla --inventory=<path> [--policy=<path>] [--output=md|json] [--fail-on-breach]
"""
}
