package com.jakubjirak.debtaccountant.sla

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import com.jakubjirak.debtaccountant.cli.CliAbort
import com.jakubjirak.debtaccountant.cli.cliAttempt
import com.jakubjirak.debtaccountant.cli.cliFail
import com.jakubjirak.debtaccountant.cli.cliHelp
import com.jakubjirak.debtaccountant.cli.cliValue
import com.jakubjirak.debtaccountant.cli.isHelpArg
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
    fun run(args: Array<String>, now: Instant = Instant.now()): Result = try {
        execute(parseArgs(args), now)
    } catch (abort: CliAbort) {
        Result(abort.exitCode, abort.stdout, abort.stderr)
    }

    private data class Options(
        val inventoryPath: String,
        val policyPath: String?,
        val output: String,
        val failOnBreach: Boolean,
    )

    private fun parseArgs(args: Array<String>): Options {
        var inventoryPath: String? = null
        var policyPath: String? = null
        var output = "md"
        var failOnBreach = false
        for (raw in args) {
            when {
                raw.startsWith("--inventory=") -> inventoryPath = raw.cliValue()
                raw.startsWith("--policy=") -> policyPath = raw.cliValue()
                raw.startsWith("--output=") -> output = raw.cliValue()
                raw == "--fail-on-breach" -> failOnBreach = true
                isHelpArg(raw) -> cliHelp(HELP)
                else -> cliFail("Unknown argument: $raw\n$HELP")
            }
        }
        if (inventoryPath == null) cliFail("Missing required --inventory=<path>\n$HELP")
        if (output != "md" && output != "json") cliFail("--output must be 'md' or 'json' (was '$output')\n")
        return Options(inventoryPath, policyPath, output, failOnBreach)
    }

    private fun loadPolicy(policyPath: String?): SlaPolicy {
        if (policyPath == null) return SlaPolicy.DEFAULT
        val p = Paths.get(policyPath)
        if (!Files.exists(p)) cliFail("Policy file not found: $policyPath\n")
        return cliAttempt("Failed to read policy") { SlaPolicyStore.fromJson(Files.readString(p)) }
    }

    private fun execute(options: Options, now: Instant): Result {
        val items = cliAttempt("Failed to read inventory") { readInventory(Paths.get(options.inventoryPath)) }
        val policy = loadPolicy(options.policyPath)

        val policyErrors = policy.validate()
        if (policyErrors.isNotEmpty()) {
            cliFail("Invalid policy:\n" + policyErrors.joinToString("\n") { "  - $it" } + "\n")
        }

        val eval = SlaEvaluator.evaluate(items, policy, now)
        val body = if (options.output == "json") {
            renderJson(eval)
        } else {
            SlaMarkdownReporter.render(eval, projectNameFromPath(options.inventoryPath))
        }
        val exit = if (options.failOnBreach && eval.summary.breachedCount > 0) 2 else 0
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
