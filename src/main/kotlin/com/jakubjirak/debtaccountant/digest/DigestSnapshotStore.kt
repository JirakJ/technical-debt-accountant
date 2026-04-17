package com.jakubjirak.debtaccountant.digest

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Persists [DigestSnapshot]s as pretty-printed JSON. Writes are atomic:
 * content is first written to `<target>.tmp` in the same directory and then
 * moved with [StandardCopyOption.ATOMIC_MOVE] / [StandardCopyOption.REPLACE_EXISTING].
 *
 * All file I/O is blocking. Callers on the EDT must wrap this in a
 * background task (see `GenerateWeeklyDigestAction`).
 */
class DigestSnapshotStore(private val baseDirectory: Path) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Root directory where snapshot files are stored. Created lazily on save. */
    val directory: Path get() = baseDirectory

    /**
     * Persist [snapshot] and return the file path it was written to. If [fileName]
     * is `null`, a deterministic name is derived from [DigestSnapshot.takenAtMillis]
     * and [DigestSnapshot.label] — e.g. `2026-04-17_last-7-days.json`.
     */
    @Throws(IOException::class)
    fun save(snapshot: DigestSnapshot, fileName: String? = null): Path {
        Files.createDirectories(baseDirectory)
        val target = baseDirectory.resolve(fileName ?: defaultName(snapshot))
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(tmp, gson.toJson(snapshot))
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    /** Load a snapshot previously written by [save]. Throws if the file does not parse. */
    @Throws(IOException::class)
    fun load(path: Path): DigestSnapshot {
        val text = Files.readString(path)
        return gson.fromJson(text, DigestSnapshot::class.java)
            ?: throw IOException("Empty or invalid snapshot file: $path")
    }

    /**
     * Returns every `*.json` file in [baseDirectory] sorted by last-modified time ascending.
     * An empty list is returned when the directory does not exist.
     */
    fun listSnapshots(): List<Path> {
        if (!Files.isDirectory(baseDirectory)) return emptyList()
        Files.newDirectoryStream(baseDirectory, "*.json").use { stream ->
            return stream.toList().sortedBy { Files.getLastModifiedTime(it).toMillis() }
        }
    }

    /** Most recent snapshot by last-modified time, or `null` if none exist. */
    fun latest(): DigestSnapshot? = listSnapshots().lastOrNull()?.let(::load)

    companion object {
        private val DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

        private fun defaultName(snapshot: DigestSnapshot): String {
            val date = DATE_FMT.format(Instant.ofEpochMilli(snapshot.takenAtMillis))
            val slug = snapshot.label
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "snapshot" }
            return "${date}_${slug}.json"
        }
    }
}
