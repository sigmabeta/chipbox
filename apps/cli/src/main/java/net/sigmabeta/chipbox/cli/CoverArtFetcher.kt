package net.sigmabeta.chipbox.cli

import kotlinx.serialization.SerializationException
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** What happened when fetching cover art for one game. */
enum class CoverArtOutcome { DOWNLOADED, UP_TO_DATE, NO_MATCH, NO_COVER, NO_FOLDER, FAILED }

/**
 * Per-game cover-art result; [detail] is the saved file name (on success) or an error message.
 * [fromCache] is true when the lookup came from the persistent cache instead of the IGDB API.
 */
data class CoverArtResult(
    val title: String,
    val outcome: CoverArtOutcome,
    val detail: String? = null,
    val fromCache: Boolean = false,
)

/** Aggregate counts across a run (so far). [fromCache] cross-cuts the outcomes, so it's not in [total]. */
data class CoverArtSummary(
    val downloaded: Int,
    val upToDate: Int,
    val noMatch: Int,
    val noCover: Int,
    val skipped: Int,
    val failed: Int,
    val fromCache: Int,
) {
    val total: Int get() = downloaded + upToDate + noMatch + noCover + skipped + failed
}

/**
 * Thread-safe running tally of per-game outcomes. The fetch loop records into it from one thread
 * while a shutdown hook can [snapshot] it from another to print a summary if the run is cut short.
 */
class CoverArtTally {
    private val downloaded = AtomicInteger()
    private val upToDate = AtomicInteger()
    private val noMatch = AtomicInteger()
    private val noCover = AtomicInteger()
    private val skipped = AtomicInteger()
    private val failed = AtomicInteger()
    private val fromCache = AtomicInteger()

    fun record(result: CoverArtResult) {
        when (result.outcome) {
            CoverArtOutcome.DOWNLOADED -> downloaded
            CoverArtOutcome.UP_TO_DATE -> upToDate
            CoverArtOutcome.NO_MATCH -> noMatch
            CoverArtOutcome.NO_COVER -> noCover
            CoverArtOutcome.NO_FOLDER -> skipped
            CoverArtOutcome.FAILED -> failed
        }.incrementAndGet()
        if (result.fromCache) fromCache.incrementAndGet()
    }

    fun snapshot(): CoverArtSummary = CoverArtSummary(
        downloaded.get(),
        upToDate.get(),
        noMatch.get(),
        noCover.get(),
        skipped.get(),
        failed.get(),
        fromCache.get(),
    )
}

/**
 * Drives the "Get cover art" run: for each game, ask [igdb] for a cover, and on a hit download the
 * image into the game's own folder as `<Title>.jpg`, first deleting any existing image there so the
 * scanner (which adopts the first jpg/png it finds) picks up the new cover on the next rescan.
 *
 * A game whose tracks are spread across several folders gets the cover written into each of them.
 * One game's failure (network, bad response) is recorded and the run continues.
 *
 * Every successful lookup is read from / written to [cache], so repeat runs skip the rate-limited
 * IGDB search API for games already looked up. Failures aren't cached (they're usually transient).
 *
 * Results are streamed to [onResult] one game at a time (rather than returned in a batch) so the
 * caller can print progress live and keep a running tally that survives an early Ctrl-C.
 */
class CoverArtFetcher(
    private val igdb: IgdbClient,
    private val httpClient: OkHttpClient,
    private val cache: CoverArtCache,
    private val overrides: CoverArtOverrides,
) {
    fun fetch(games: List<Game>, onResult: (CoverArtResult) -> Unit) {
        for (game in games) {
            onResult(process(game))
        }
    }

    private fun process(game: Game): CoverArtResult {
        val folders = trackFolders(game)
        if (folders.isEmpty()) return CoverArtResult(game.title, CoverArtOutcome.NO_FOLDER)
        return try {
            val platforms = game.tracks.orEmpty().map { it.platform }.toSet()
            // A user override wins over the cache and the automatic name search.
            val pinned = overrides.imageId(game.title, platforms)?.let { CoverLookup.Found(it) }
            val cached = pinned ?: cache.get(game.title, platforms)
            val lookup = cached ?: igdb.lookupCover(game.title, platforms).also {
                cache.recordLookup(game.title, platforms, it)
            }
            val fromCache = cached != null
            when (lookup) {
                CoverLookup.NoMatch -> CoverArtResult(game.title, CoverArtOutcome.NO_MATCH, fromCache = fromCache)
                CoverLookup.NoCover -> CoverArtResult(game.title, CoverArtOutcome.NO_COVER, fromCache = fromCache)
                is CoverLookup.Found -> obtain(game.title, platforms, lookup.imageId, folders, fromCache)
            }
        } catch (error: IOException) {
            CoverArtResult(game.title, CoverArtOutcome.FAILED, error.message)
        } catch (error: SerializationException) {
            CoverArtResult(game.title, CoverArtOutcome.FAILED, error.message)
        }
    }

    // Downloads the cover for [imageId] into every target folder, replacing any existing image —
    // unless that exact URL is already the cover on disk in all of them, in which case the bytes are
    // unchanged (IGDB URLs are content-addressed) and we skip the download entirely.
    private fun obtain(
        title: String,
        platforms: Set<Platform>,
        imageId: String,
        folders: List<File>,
        fromCache: Boolean,
    ): CoverArtResult {
        val url = igdb.coverUrl(imageId)
        val fileName = sanitize(title) + IMAGE_EXTENSION
        val alreadyCurrent = cache.downloadedUrl(title, platforms) == url &&
            folders.all { File(it, fileName).isFile }
        if (alreadyCurrent) return CoverArtResult(title, CoverArtOutcome.UP_TO_DATE, fileName, fromCache)

        val bytes = download(url)
        for (folder in folders) {
            removeExistingImages(folder)
            File(folder, fileName).writeBytes(bytes)
        }
        cache.recordDownload(title, platforms, url)
        return CoverArtResult(title, CoverArtOutcome.DOWNLOADED, fileName, fromCache)
    }

    private fun download(imageUrl: String): ByteArray {
        val request = Request.Builder().url(imageUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image download failed: ${response.code}")
            return response.body.bytes()
        }
    }

    /** Distinct on-disk folders that hold this game's tracks (usually one). */
    private fun trackFolders(game: Game): List<File> = game.tracks.orEmpty()
        .mapNotNull { File(it.path).parentFile }
        .filter { it.isDirectory }
        .distinctBy { canonicalOrNull(it) ?: it.absolutePath }

    private fun removeExistingImages(folder: File) {
        folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.forEach { it.delete() }
    }

    private fun sanitize(name: String): String {
        val cleaned = name
            .map { if (it.isISOControl() || it in ILLEGAL_CHARS) '_' else it }
            .joinToString("")
            .trim()
            .trimEnd('.')
        return cleaned.ifBlank { "cover" }
    }

    private fun canonicalOrNull(file: File): String? = runCatching { file.canonicalPath }.getOrNull()

    private companion object {
        const val IMAGE_EXTENSION = ".jpg"
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        val ILLEGAL_CHARS = "\\/:*?\"<>|".toSet()
    }
}
