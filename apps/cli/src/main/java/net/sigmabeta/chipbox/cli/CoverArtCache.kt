package net.sigmabeta.chipbox.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.models.Platform
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared key for cover-art lookups and overrides: a game's title plus its platform set, which
 * together determine which IGDB cover applies. Used by both [CoverArtCache] and [CoverArtOverrides]
 * so an override lines up with the cache entry it supersedes.
 */
internal fun coverArtKey(title: String, platforms: Set<Platform>): String =
    title + "|" + platforms.map { it.name }.sorted().joinToString(",")

/**
 * Persistent cache of IGDB cover-art lookups, keyed on the inputs to [IgdbClient.lookupCover] (game
 * title + platform set). Lets repeated runs skip the rate-limited IGDB search API for games already
 * looked up. Stored as a small JSON file under the work dir; entries older than [CACHE_TTL_DAYS] are
 * treated as misses and re-queried, so titles IGDB adds later eventually get picked up.
 *
 * Thread-safe: the fetch loop reads and writes it while a Ctrl-C shutdown hook may [save] it.
 */
class CoverArtCache(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val lock = Any()
    private val entries = linkedMapOf<String, CacheEntry>()

    init {
        if (file.isFile) {
            runCatching { json.decodeFromString<Map<String, CacheEntry>>(file.readText()) }
                .getOrNull()
                ?.let { entries.putAll(it) }
        }
    }

    /** Cached lookup for [title]/[platforms], or null on a miss (absent, stale, or malformed). */
    fun get(title: String, platforms: Set<Platform>): CoverLookup? = synchronized(lock) {
        entries[keyOf(title, platforms)]?.takeIf { it.isFresh() && it.isValid() }?.toLookup()
    }

    /** Records a lookup result, preserving any previously recorded download URL for this key. */
    fun recordLookup(title: String, platforms: Set<Platform>, result: CoverLookup) = synchronized(lock) {
        val key = keyOf(title, platforms)
        entries[key] = result.toEntry(downloadedUrl = entries[key]?.downloadedUrl)
    }

    /** Notes that [url]'s bytes are the cover currently on disk for [title]/[platforms]. */
    fun recordDownload(title: String, platforms: Set<Platform>, url: String) = synchronized(lock) {
        val key = keyOf(title, platforms)
        entries[key] = entries[key]?.copy(downloadedUrl = url) ?: CacheEntry(CacheKind.FOUND, url, url)
    }

    /**
     * The URL whose bytes were last written to disk for [title]/[platforms], if any. IGDB cover URLs
     * are content-addressed (they embed the image's id), so an unchanged URL means unchanged bytes —
     * the caller can skip re-downloading. Not gated on TTL: the file on disk is valid regardless.
     */
    fun downloadedUrl(title: String, platforms: Set<Platform>): String? = synchronized(lock) {
        entries[keyOf(title, platforms)]?.downloadedUrl
    }

    /** Persists the current entries to disk. Safe to call more than once. */
    fun save() = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(entries.toMap()))
    }

    private fun keyOf(title: String, platforms: Set<Platform>): String = coverArtKey(title, platforms)

    private fun CacheEntry.isFresh(): Boolean =
        System.currentTimeMillis() - epochMillis <= TimeUnit.DAYS.toMillis(CACHE_TTL_DAYS)

    private fun CacheEntry.isValid(): Boolean = kind != CacheKind.FOUND || imageId != null

    private fun CacheEntry.toLookup(): CoverLookup = when (kind) {
        CacheKind.FOUND -> CoverLookup.Found(imageId.orEmpty())
        CacheKind.NO_COVER -> CoverLookup.NoCover
        CacheKind.NO_MATCH -> CoverLookup.NoMatch
    }

    private fun CoverLookup.toEntry(downloadedUrl: String?): CacheEntry = when (this) {
        is CoverLookup.Found -> CacheEntry(CacheKind.FOUND, imageId, downloadedUrl)
        CoverLookup.NoCover -> CacheEntry(CacheKind.NO_COVER, null, downloadedUrl)
        CoverLookup.NoMatch -> CacheEntry(CacheKind.NO_MATCH, null, downloadedUrl)
    }

    private companion object {
        const val CACHE_TTL_DAYS = 180L
    }
}

@Serializable
private enum class CacheKind { FOUND, NO_MATCH, NO_COVER }

@Serializable
private data class CacheEntry(
    val kind: CacheKind,
    val imageId: String? = null,
    val downloadedUrl: String? = null,
    val epochMillis: Long = System.currentTimeMillis(),
)
