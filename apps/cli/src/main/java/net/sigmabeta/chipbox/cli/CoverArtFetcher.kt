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
 * scanner (which adopts the first jpg/png it finds) picks up the new cover on the next rescan. A
 * human-readable `igdb.txt` describing the match is written alongside it (see [IgdbLinkFile]).
 *
 * A game whose tracks are spread across several folders gets the cover (and descriptor) written into
 * each of them. One game's failure (network, bad response) is recorded and the run continues.
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
            // Resolve the match without touching IGDB if we can: a user override wins, then the cache,
            // then an igdb.txt already in the game's folder (a prior match that survived a cleared
            // cache or a moved library). Reading the folder file back into the cache keeps later games
            // and runs fast and seeds the correct image id for the download step.
            val pinned = overrides.get(game.title, platforms)
                ?.let { CoverLookup.Found(it.imageId, it.igdbId, it.igdbName, it.igdbSlug) }
            val cached = pinned
                ?: cache.get(game.title, platforms)
                ?: IgdbLinkFile.read(folders)?.also { cache.recordLookup(game.title, platforms, it) }
            val lookup = cached ?: igdb.lookupCover(game.title, platforms).also {
                cache.recordLookup(game.title, platforms, it)
            }
            val fromCache = cached != null
            when (lookup) {
                CoverLookup.NoMatch -> CoverArtResult(game.title, CoverArtOutcome.NO_MATCH, fromCache = fromCache)

                is CoverLookup.NoCover -> {
                    IgdbLinkFile.writeInto(folders, game.title, platforms, lookup, refreshDate = !fromCache)
                    CoverArtResult(game.title, CoverArtOutcome.NO_COVER, fromCache = fromCache)
                }

                is CoverLookup.Found -> obtain(game.title, platforms, lookup, folders, fromCache)
            }
        } catch (error: IOException) {
            CoverArtResult(game.title, CoverArtOutcome.FAILED, error.message)
        } catch (error: SerializationException) {
            CoverArtResult(game.title, CoverArtOutcome.FAILED, error.message)
        }
    }

    // Downloads the cover for [found] into every target folder, replacing any existing image —
    // unless that exact URL is already the cover on disk in all of them, in which case the bytes are
    // unchanged (IGDB URLs are content-addressed) and we skip the download entirely. Either way, an
    // igdb.txt describing the match is (re)written into each folder afterwards.
    private fun obtain(
        title: String,
        platforms: Set<Platform>,
        found: CoverLookup.Found,
        folders: List<File>,
        fromCache: Boolean,
    ): CoverArtResult {
        val url = igdb.coverUrl(found.imageId)
        val fileName = sanitize(title) + IMAGE_EXTENSION
        val alreadyCurrent = cache.downloadedUrl(title, platforms) == url &&
            folders.all { File(it, fileName).isFile }
        val outcome = if (alreadyCurrent) {
            CoverArtOutcome.UP_TO_DATE
        } else {
            val bytes = download(url)
            for (folder in folders) {
                removeExistingImages(folder)
                File(folder, fileName).writeBytes(bytes)
            }
            cache.recordDownload(title, platforms, url)
            CoverArtOutcome.DOWNLOADED
        }
        // refreshDate when the match came fresh from IGDB (not from a local source); see IgdbLinkFile.
        IgdbLinkFile.writeInto(folders, title, platforms, found, refreshDate = !fromCache)
        return CoverArtResult(title, outcome, fileName, fromCache)
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
