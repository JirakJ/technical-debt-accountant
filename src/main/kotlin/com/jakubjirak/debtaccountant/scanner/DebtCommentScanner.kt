package com.jakubjirak.debtaccountant.scanner

import com.jakubjirak.debtaccountant.model.TechDebtItem
import java.nio.file.Files
import java.nio.file.Path

/**
 * Scans source text for debt-marker comments (`TODO`, `FIXME`, `HACK`, `XXX`,
 * `OPTIMIZE`) and converts them into [TechDebtItem]s ready for the inventory.
 *
 * Pure logic — no IntelliJ platform dependencies — so it is fully unit-testable
 * and reusable from [DebtScanCli] in CI pipelines.
 *
 * Detection rules:
 *  - the marker must be uppercase and a whole word (`TODO`, not `todo` or `TODOS`);
 *  - the marker must appear after a comment token (slashes, hash, asterisk,
 *    semicolon, double dash, block- or HTML-comment opener) on the same line,
 *    so plain string occurrences are ignored;
 *  - an optional `(author)` and an optional `:` after the marker are stripped
 *    from the captured text.
 */
object DebtCommentScanner {

    /** Tag attached to every imported item so re-scans can recognize them. */
    const val SCAN_TAG = "scanned"

    private val markerAlternatives = DebtMarker.entries.joinToString("|") { it.name }

    private val markerRegex = Regex(
        """(?://|#|\*|;|--|/\*|<!--)\s*($markerAlternatives)\b\s*(?:\(([^)]*)\))?\s*:?\s*(.*)"""
    )

    private val skippedDirectories = setOf(
        ".git", ".hg", ".svn", ".idea", ".gradle", ".tech-debt",
        "build", "out", "dist", "target", "node_modules", "vendor", "venv", ".venv"
    )

    private val skippedExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "svg", "pdf", "zip", "jar", "gz", "tar",
        "class", "exe", "dll", "so", "dylib", "bin", "woff", "woff2", "ttf", "eot",
        "mp3", "mp4", "webm", "lock", "min.js", "min.css"
    )

    private const val MAX_FILE_BYTES = 1_000_000L
    private const val MAX_TEXT_LENGTH = 200

    /** Scans a single file's [text], returning one [ScannedComment] per marker hit. */
    fun scanText(text: String, filePath: String): List<ScannedComment> {
        val results = mutableListOf<ScannedComment>()
        text.lineSequence().forEachIndexed { index, line ->
            val match = markerRegex.find(line) ?: return@forEachIndexed
            val marker = DebtMarker.valueOf(match.groupValues[1])
            val comment = match.groupValues[3]
                .removeSuffix("*/").removeSuffix("-->")
                .trim()
                .take(MAX_TEXT_LENGTH)
            results.add(ScannedComment(filePath, index + 1, marker, comment))
        }
        return results
    }

    /**
     * Walks [root] recursively and scans every text file, skipping VCS/build/IDE
     * directories, binary extensions, and files larger than 1 MB. Returned
     * [ScannedComment.filePath]s are relative to [root] with `/` separators.
     */
    fun scanDirectory(root: Path): List<ScannedComment> {
        if (!Files.isDirectory(root)) return emptyList()
        val results = mutableListOf<ScannedComment>()
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path -> !isSkipped(root, path) }
                .sorted()
                .forEach { path ->
                    val text = readTextOrNull(path) ?: return@forEach
                    val relative = root.relativize(path).toString().replace('\\', '/')
                    results.addAll(scanText(text, relative))
                }
        }
        return results
    }

    /**
     * Drops scanned comments that already exist in [existing]: same file path and
     * either the same line (for a [SCAN_TAG]-tagged item) or the same description.
     */
    fun dedupe(scanned: List<ScannedComment>, existing: List<TechDebtItem>): List<ScannedComment> {
        val byLine = existing
            .filter { SCAN_TAG in it.tags }
            .map { it.filePath to it.lineStart }
            .toHashSet()
        val byDescription = existing.map { it.filePath to it.description }.toHashSet()
        return scanned.filter { comment ->
            (comment.filePath to comment.line) !in byLine &&
                (comment.filePath to describe(comment)) !in byDescription
        }
    }

    /** Converts a scanned comment to an inventory item using the marker's defaults. */
    fun toDebtItem(comment: ScannedComment): TechDebtItem =
        TechDebtItem(
            filePath = comment.filePath,
            lineStart = comment.line,
            lineEnd = comment.line,
            category = comment.marker.category,
            priority = comment.marker.priority,
            description = describe(comment),
            estimatedFixMinutes = comment.marker.defaultFixMinutes,
            tags = mutableSetOf(SCAN_TAG, comment.marker.name.lowercase())
        )

    /** Description used for imported items: `FIXME: comment text` (marker alone if no text). */
    fun describe(comment: ScannedComment): String =
        if (comment.text.isEmpty()) comment.marker.name else "${comment.marker.name}: ${comment.text}"

    private fun isSkipped(root: Path, path: Path): Boolean {
        val relative = root.relativize(path)
        for (i in 0 until relative.nameCount - 1) {
            val segment = relative.getName(i).toString()
            if (segment in skippedDirectories || segment.startsWith(".")) return true
        }
        val name = path.fileName.toString().lowercase()
        val extension = name.substringAfterLast('.', "")
        if (extension in skippedExtensions || name.endsWith(".min.js") || name.endsWith(".min.css")) return true
        return runCatching { Files.size(path) > MAX_FILE_BYTES }.getOrDefault(true)
    }

    private fun readTextOrNull(path: Path): String? =
        runCatching {
            val bytes = Files.readAllBytes(path)
            if (bytes.any { it == 0.toByte() }) return null
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
}
