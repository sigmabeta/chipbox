package net.sigmabeta.chipbox.coverart.real

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.coverart.COVER_ART_TTL_DAYS
import net.sigmabeta.chipbox.coverart.CoverLookup
import net.sigmabeta.chipbox.coverart.coverArtKey
import net.sigmabeta.chipbox.models.Platform
import okio.FileSystem
import okio.Path

/**
 * Persistent cache of IGDB cover-art lookups, keyed on the inputs to [IgdbClient.lookupCover] (game
 * title + platform set). Lets repeated runs skip the rate-limited IGDB search API for games already
 * looked up. Stored as a small JSON file on [fileSystem]; entries older than [COVER_ART_TTL_DAYS] are
 * treated as misses and re-queried, so titles IGDB adds later eventually get picked up.
 *
 * Thread-safe: entries are held in an [AtomicReference] to an immutable map, so the fetch loop reads
 * and updates them while a Ctrl-C shutdown hook may [save].
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)
class CoverArtCache(
    private val fileSystem: FileSystem,
    private val file: Path,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val entries = AtomicReference<Map<String, CacheEntry>>(loadFromDisk())

    /** Cached lookup for [title]/[platforms], or null on a miss (absent, stale, or malformed). */
    fun get(title: String, platforms: Set<Platform>): CoverLookup? =
        entries.load()[keyOf(title, platforms)]?.takeIf { it.isFresh() && it.isValid() }?.toLookup()

    /** Records a lookup result, preserving any previously recorded download URL for this key. */
    fun recordLookup(title: String, platforms: Set<Platform>, result: CoverLookup) {
        val key = keyOf(title, platforms)
        entries.update { it + (key to result.toEntry(downloadedUrl = it[key]?.downloadedUrl)) }
    }

    /** Notes that [url]'s bytes are the cover currently on disk for [title]/[platforms]. */
    fun recordDownload(title: String, platforms: Set<Platform>, url: String) {
        val key = keyOf(title, platforms)
        entries.update {
            val updated = it[key]?.copy(downloadedUrl = url) ?: CacheEntry(CacheKind.FOUND, url, url)
            it + (key to updated)
        }
    }

    /**
     * The URL whose bytes were last written to disk for [title]/[platforms], if any. IGDB cover URLs
     * are content-addressed (they embed the image's id), so an unchanged URL means unchanged bytes —
     * the caller can skip re-downloading. Not gated on TTL: the file on disk is valid regardless.
     */
    fun downloadedUrl(title: String, platforms: Set<Platform>): String? =
        entries.load()[keyOf(title, platforms)]?.downloadedUrl

    /** Persists the current entries to disk. Safe to call more than once. */
    fun save() {
        file.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(file) { writeUtf8(json.encodeToString(entries.load())) }
    }

    private fun loadFromDisk(): Map<String, CacheEntry> {
        if (fileSystem.metadataOrNull(file)?.isRegularFile != true) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, CacheEntry>>(fileSystem.read(file) { readUtf8() })
        }.getOrNull() ?: emptyMap()
    }

    private fun keyOf(title: String, platforms: Set<Platform>): String = coverArtKey(title, platforms)

    private fun CacheEntry.isFresh(): Boolean =
        Clock.System.now().toEpochMilliseconds() - epochMillis <= COVER_ART_TTL_DAYS.days.inWholeMilliseconds

    private fun CacheEntry.isValid(): Boolean = kind != CacheKind.FOUND || imageId != null

    private fun CacheEntry.toLookup(): CoverLookup = when (kind) {
        CacheKind.FOUND -> CoverLookup.Found(imageId.orEmpty(), igdbId, igdbName, igdbSlug)
        CacheKind.NO_COVER -> CoverLookup.NoCover(igdbId, igdbName, igdbSlug)
        CacheKind.NO_MATCH -> CoverLookup.NoMatch
    }

    private fun CoverLookup.toEntry(downloadedUrl: String?): CacheEntry = when (this) {
        is CoverLookup.Found -> CacheEntry(
            kind = CacheKind.FOUND,
            imageId = imageId,
            downloadedUrl = downloadedUrl,
            igdbId = igdbId,
            igdbName = igdbName,
            igdbSlug = igdbSlug,
        )

        is CoverLookup.NoCover -> CacheEntry(
            kind = CacheKind.NO_COVER,
            downloadedUrl = downloadedUrl,
            igdbId = igdbId,
            igdbName = igdbName,
            igdbSlug = igdbSlug,
        )

        CoverLookup.NoMatch -> CacheEntry(CacheKind.NO_MATCH, null, downloadedUrl)
    }
}

@Serializable
private enum class CacheKind { FOUND, NO_MATCH, NO_COVER }

@OptIn(ExperimentalTime::class)
@Serializable
private data class CacheEntry(
    val kind: CacheKind,
    val imageId: String? = null,
    val downloadedUrl: String? = null,
    val igdbId: String? = null,
    val igdbName: String? = null,
    val igdbSlug: String? = null,
    val epochMillis: Long = Clock.System.now().toEpochMilliseconds(),
)
