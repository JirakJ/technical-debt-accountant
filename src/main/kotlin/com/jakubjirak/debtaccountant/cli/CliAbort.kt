package com.jakubjirak.debtaccountant.cli

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Aborts a CLI run with a ready-made outcome. Thrown by the helpers below and
 * caught once at the top of each CLI's `run`, so argument parsing and I/O steps
 * can stay linear instead of threading early returns through every branch.
 */
internal class CliAbort(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    cause: Throwable? = null,
) : RuntimeException(stderr.ifEmpty { stdout }, cause)

/** Stops the run with exit code [exitCode] and [message] on stderr. */
internal fun cliFail(message: String, exitCode: Int = 1, cause: Throwable? = null): Nothing =
    throw CliAbort(exitCode, "", message, cause)

/** Stops the run successfully, printing [text] (used for `--help`). */
internal fun cliHelp(text: String): Nothing = throw CliAbort(0, text, "")

/**
 * Runs [block]; any exception other than a [CliAbort] becomes a failure whose
 * stderr is `"$what: <message>"`.
 */
internal inline fun <T> cliAttempt(what: String, block: () -> T): T =
    try {
        block()
    } catch (e: CliAbort) {
        throw e
    } catch (e: Exception) {
        cliFail("$what: ${e.message}\n", cause = e)
    }

/** Writes [body] to [path], creating parent directories as needed. */
internal fun cliWriteFile(path: String, body: String) {
    cliAttempt("Failed to write output") {
        val target = Paths.get(path)
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, body)
    }
}

/** Value of a `--name=value` argument. */
internal fun String.cliValue(): String = substringAfter('=')

/** True when [arg] asks for usage help. */
internal fun isHelpArg(arg: String): Boolean = arg == "--help" || arg == "-h"
