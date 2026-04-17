package com.jakubjirak.debtaccountant.sla

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Pure-Kotlin loader/saver for [SlaPolicy] documents persisted at
 * `<projectRoot>/.debt-sla.json`. No IntelliJ dependencies — usable from CLI.
 */
object SlaPolicyStore {

    /** Relative filename for the policy document. */
    const val FILE_NAME: String = ".debt-sla.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Load the policy for [projectRoot]. Returns [SlaPolicy.DEFAULT] when the file is missing
     * or cannot be parsed. Throws no checked exceptions.
     */
    fun load(projectRoot: Path): SlaPolicy {
        val file = projectRoot.resolve(FILE_NAME)
        if (!Files.exists(file)) return SlaPolicy.DEFAULT
        return try {
            val json = Files.readString(file)
            gson.fromJson(json, SlaPolicy::class.java) ?: SlaPolicy.DEFAULT
        } catch (_: Exception) {
            SlaPolicy.DEFAULT
        }
    }

    /**
     * Atomically save [policy] to `<projectRoot>/.debt-sla.json`. Writes to a sibling
     * `.debt-sla.json.tmp` first, then moves into place. On happy path the temp file
     * does not remain behind.
     */
    fun save(policy: SlaPolicy, projectRoot: Path) {
        Files.createDirectories(projectRoot)
        val target = projectRoot.resolve(FILE_NAME)
        val tmp = projectRoot.resolve("$FILE_NAME.tmp")
        Files.writeString(tmp, gson.toJson(policy))
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (Files.exists(tmp)) {
                runCatching { Files.delete(tmp) }
            }
        }
    }

    /** Serialize [policy] to pretty JSON (used by tests and policy editor action). */
    fun toJson(policy: SlaPolicy): String = gson.toJson(policy)

    /** Parse a policy JSON string. Returns [SlaPolicy.DEFAULT] on failure. */
    fun fromJson(json: String): SlaPolicy =
        try { gson.fromJson(json, SlaPolicy::class.java) ?: SlaPolicy.DEFAULT } catch (_: Exception) { SlaPolicy.DEFAULT }
}
