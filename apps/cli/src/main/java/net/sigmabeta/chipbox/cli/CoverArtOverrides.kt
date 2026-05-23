package net.sigmabeta.chipbox.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.models.Platform
import java.io.File

/** A manual link from a Chipbox game to a specific IGDB game's cover. */
@Serializable
data class OverrideEntry(
    val igdbId: String,
    val igdbName: String,
    val imageId: String,
)

/**
 * Persistent, user-set overrides that pin a Chipbox game to a specific IGDB game's cover, taking
 * precedence over the automatic name search. Kept in its own JSON file (separate from the derived
 * [CoverArtCache]) so it never expires and survives clearing the cache.
 */
class CoverArtOverrides(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val lock = Any()
    private val entries = linkedMapOf<String, OverrideEntry>()

    init {
        if (file.isFile) {
            runCatching { json.decodeFromString<Map<String, OverrideEntry>>(file.readText()) }
                .getOrNull()
                ?.let { entries.putAll(it) }
        }
    }

    /** The pinned cover image id for [title]/[platforms], or null if there's no override. */
    fun imageId(title: String, platforms: Set<Platform>): String? = synchronized(lock) {
        entries[coverArtKey(title, platforms)]?.imageId
    }

    /** The full override for [title]/[platforms] (for display), or null if there's none. */
    fun get(title: String, platforms: Set<Platform>): OverrideEntry? = synchronized(lock) {
        entries[coverArtKey(title, platforms)]
    }

    fun set(title: String, platforms: Set<Platform>, entry: OverrideEntry) = synchronized(lock) {
        entries[coverArtKey(title, platforms)] = entry
        save()
    }

    /** Removes any override for [title]/[platforms]; returns true if one existed. */
    fun clear(title: String, platforms: Set<Platform>): Boolean = synchronized(lock) {
        val removed = entries.remove(coverArtKey(title, platforms)) != null
        if (removed) save()
        removed
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(entries.toMap()))
    }
}
