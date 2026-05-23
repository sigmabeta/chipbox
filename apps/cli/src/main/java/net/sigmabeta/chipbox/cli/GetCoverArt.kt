package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.models.Game
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Get cover art" flow. Reads IGDB credentials from the library's config file (writing a template
 * and bailing out with instructions if they're missing), then searches IGDB for every scanned game
 * and downloads each cover into the game's folder, printing one line of progress per game and a
 * summary at the end.
 *
 * Lookups are cached to disk and reused on later runs, so re-running only queries IGDB for games it
 * hasn't seen. The cache is saved on normal completion and on an early Ctrl-C, whose shutdown hook
 * also prints a summary of the work done so far.
 */
class GetCoverArt(
    private val terminal: Terminal,
    private val library: ChipboxLibrary,
) {
    fun run() {
        val credentials = CoverArtConfig.load(library.coverArtConfigFile)
        if (credentials == null) {
            reportMissingConfig()
            return
        }
        val games = runBlocking { library.gamesWithTracks() }
        if (games.isEmpty()) {
            terminal.println("No games in the library yet — scan one first.")
            return
        }
        downloadFor(credentials, games)
    }

    private fun downloadFor(credentials: IgdbCredentials, games: List<Game>) {
        terminal.println("Searching IGDB for cover art for ${games.size} game(s)...")
        terminal.println(gray("Press Ctrl-C to stop early and see a summary of what's done."))
        val httpClient = OkHttpClient()
        val cache = CoverArtCache(library.coverArtCacheFile)
        val overrides = CoverArtOverrides(library.coverArtOverridesFile)
        val tally = CoverArtTally()
        val finished = AtomicBoolean(false)
        // On Ctrl-C the JVM runs this before halting; persist the cache and print what's been done so
        // far (unless the run already finished and printed its own summary).
        val hook = Thread {
            synchronized(printLock) {
                if (!finished.get()) printSummary(tally.snapshot(), stoppedEarly = true)
            }
            cache.save()
        }
        val runtime = Runtime.getRuntime()
        runtime.addShutdownHook(hook)
        try {
            val fetcher = CoverArtFetcher(IgdbClient(credentials, httpClient), httpClient, cache, overrides)
            fetcher.fetch(games) { result ->
                tally.record(result)
                synchronized(printLock) { printResult(result) }
            }
            finished.set(true)
            synchronized(printLock) { printSummary(tally.snapshot(), stoppedEarly = false) }
        } finally {
            runCatching { runtime.removeShutdownHook(hook) }
            cache.save()
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
    }

    private fun reportMissingConfig() {
        CoverArtConfig.writeTemplate(library.coverArtConfigFile)
        terminal.println(yellow("IGDB credentials not found."))
        terminal.println("Add your Twitch client_id / client_secret to:")
        terminal.println("  ${gray(library.coverArtConfigFile.absolutePath)}")
        terminal.println("Register an app at ${gray("https://dev.twitch.tv")}, then run this again.")
    }

    private fun printResult(result: CoverArtResult) {
        val title = result.title
        val line = when (result.outcome) {
            CoverArtOutcome.DOWNLOADED -> "${brightGreen("+")} $title ${gray("→ ${result.detail}")}"
            CoverArtOutcome.UP_TO_DATE -> "${gray("=")} $title ${gray("(up to date)")}"
            CoverArtOutcome.NO_MATCH -> "${gray("?")} $title ${gray("(no match)")}"
            CoverArtOutcome.NO_COVER -> "${gray("·")} $title ${gray("(no cover art)")}"
            CoverArtOutcome.NO_FOLDER -> "${gray("·")} $title ${gray("(no folder on disk)")}"
            CoverArtOutcome.FAILED -> "${brightRed("✗")} $title ${gray("(${result.detail ?: "failed"})")}"
        }
        terminal.println(line)
    }

    private fun printSummary(summary: CoverArtSummary, stoppedEarly: Boolean) {
        val header = if (stoppedEarly) "Stopped early after" else "Done —"
        terminal.println("")
        terminal.println("$header ${summary.total} game(s) processed:")
        terminal.println("  ${brightGreen("${summary.downloaded} cover(s) downloaded")}")
        if (summary.upToDate > 0) terminal.println("  ${summary.upToDate} already up to date")
        terminal.println("  ${summary.noMatch} not found on IGDB")
        terminal.println("  ${summary.noCover} matched but had no cover art")
        if (summary.skipped > 0) terminal.println("  ${summary.skipped} skipped (no folder on disk)")
        if (summary.failed > 0) terminal.println("  ${brightRed("${summary.failed} failed")}")
        if (summary.fromCache > 0) terminal.println(gray("  ${summary.fromCache} served from cache (no IGDB query)"))
    }

    // Serializes progress lines against the Ctrl-C hook's summary so they can't interleave.
    private val printLock = Any()
}
