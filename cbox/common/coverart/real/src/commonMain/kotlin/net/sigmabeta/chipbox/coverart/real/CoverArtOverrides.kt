package net.sigmabeta.chipbox.coverart.real

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.coverart.OverrideEntry
import net.sigmabeta.chipbox.coverart.coverArtKey
import net.sigmabeta.chipbox.models.Platform
import okio.FileSystem
import okio.Path

/**
 * Persistent, user-set overrides that pin a Chipbox game to a specific IGDB game's cover, taking
 * precedence over the automatic name search. Kept in its own JSON file (separate from the derived
 * [CoverArtCache]) so it never expires and survives clearing the cache.
 */
@OptIn(ExperimentalAtomicApi::class)
class CoverArtOverrides(
    private val fileSystem: FileSystem,
    private val file: Path,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val entries = AtomicReference<Map<String, OverrideEntry>>(loadFromDisk())

    /** The pinned cover image id for [title]/[platforms], or null if there's no override. */
    fun imageId(title: String, platforms: Set<Platform>): String? =
        entries.load()[coverArtKey(title, platforms)]?.imageId

    /** The full override for [title]/[platforms] (for display), or null if there's none. */
    fun get(title: String, platforms: Set<Platform>): OverrideEntry? =
        entries.load()[coverArtKey(title, platforms)]

    fun set(title: String, platforms: Set<Platform>, entry: OverrideEntry) {
        entries.update { it + (coverArtKey(title, platforms) to entry) }
        save()
    }

    /** Removes any override for [title]/[platforms]; returns true if one existed. */
    fun clear(title: String, platforms: Set<Platform>): Boolean {
        val key = coverArtKey(title, platforms)
        val existed = entries.load().containsKey(key)
        if (existed) {
            entries.update { it - key }
            save()
        }
        return existed
    }

    private fun loadFromDisk(): Map<String, OverrideEntry> {
        if (fileSystem.metadataOrNull(file)?.isRegularFile != true) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, OverrideEntry>>(fileSystem.read(file) { readUtf8() })
        }.getOrNull() ?: emptyMap()
    }

    private fun save() {
        file.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(file) { writeUtf8(json.encodeToString(entries.load())) }
    }
}
