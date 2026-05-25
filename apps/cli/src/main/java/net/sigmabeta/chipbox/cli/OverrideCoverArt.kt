package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.coverart.CoverArtOutcome
import net.sigmabeta.chipbox.coverart.CoverArtResult
import net.sigmabeta.chipbox.coverart.CoverLookup
import net.sigmabeta.chipbox.coverart.IgdbCredentials
import net.sigmabeta.chipbox.coverart.OverrideEntry
import net.sigmabeta.chipbox.coverart.real.CoverArtCache
import net.sigmabeta.chipbox.coverart.real.CoverArtConfig
import net.sigmabeta.chipbox.coverart.real.CoverArtFetcher
import net.sigmabeta.chipbox.coverart.real.CoverArtHttp
import net.sigmabeta.chipbox.coverart.real.CoverArtOverrides
import net.sigmabeta.chipbox.coverart.real.IgdbClient
import net.sigmabeta.chipbox.coverart.real.OkHttpCoverArtHttp
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import okhttp3.OkHttpClient
import okio.FileSystem

/**
 * "Link a game to an IGDB ID" flow. When the automatic name search picks the wrong game (or none),
 * the user finds the right one on igdb.com and pins it here by id or slug — e.g. "Air Management is
 * IGDB 1234". The game to fix can be chosen from the list of games that currently have no IGDB cover
 * link, or found by name. The override is stored (taking precedence on later "Get cover art" runs)
 * and the chosen cover is downloaded right away.
 */
class OverrideCoverArt(
    private val terminal: Terminal,
    private val library: ChipboxLibrary,
) {
    fun run() {
        val credentials = CoverArtConfig.load(FileSystem.SYSTEM, library.coverArtConfigFile)
        val games = if (credentials == null) emptyList() else runBlocking { library.gamesWithTracks() }
        when {
            credentials == null -> reportMissingConfig()
            games.isEmpty() -> terminal.println("No games in the library yet — scan one first.")
            else -> chooseAndLink(credentials, games)
        }
    }

    private fun chooseAndLink(credentials: IgdbCredentials, games: List<Game>) {
        val overrides = CoverArtOverrides(FileSystem.SYSTEM, library.coverArtOverridesFile)
        val cache = CoverArtCache(FileSystem.SYSTEM, library.coverArtCacheFile)
        // Loop back to "Link which game?" after each successful link so several games can be linked in
        // one sitting; the just-linked game drops out of the unlinked list on the next pass.
        do {
            val game = pickGame(games, cache, overrides) ?: return
            val linked = link(credentials, overrides, cache, game)
        } while (linked)
    }

    private fun pickGame(games: List<Game>, cache: CoverArtCache, overrides: CoverArtOverrides): Game? {
        val choice = terminal.interactiveSelectList(listOf(LIST_ROW, SEARCH_ROW, CANCEL), title = "Link which game?")
        return when (choice) {
            LIST_ROW -> pickUnlinked(games, cache, overrides)
            SEARCH_ROW -> pickByName(games)
            else -> null
        }
    }

    private fun pickUnlinked(games: List<Game>, cache: CoverArtCache, overrides: CoverArtOverrides): Game? {
        val unlinked = games.filter { !isLinked(it, cache, overrides) }.sortedBy { it.title.lowercase() }
        if (unlinked.isEmpty()) {
            terminal.println("Every game already has an IGDB cover link.")
            return null
        }
        terminal.println(gray("${unlinked.size} game(s) without a cover link."))
        return selectFrom(unlinked)
    }

    private fun pickByName(games: List<Game>): Game? {
        val query = terminal.prompt("Find a game (type part of its name)")?.trim().orEmpty()
        val matches = games.filter { it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
        return when {
            query.isEmpty() -> null
            matches.isEmpty() -> null.also { terminal.println("No games match “$query”.") }
            else -> selectFrom(matches)
        }
    }

    private fun selectFrom(games: List<Game>): Game? {
        // Ordinal prefixes keep every row unique, so the selected string maps back unambiguously.
        val labels = games.mapIndexed { index, game -> "${index + 1}. ${gameLabel(game)}" }
        val choice = terminal.interactiveSelectList(labels + CANCEL, title = "Select a game")
        return if (choice == null || choice == CANCEL) null else games[labels.indexOf(choice)]
    }

    // A game is "linked" if the user has pinned it or the cache already holds an IGDB cover for it.
    private fun isLinked(game: Game, cache: CoverArtCache, overrides: CoverArtOverrides): Boolean {
        val platforms = platformsOf(game)
        return overrides.imageId(game.title, platforms) != null ||
            cache.get(game.title, platforms) is CoverLookup.Found
    }

    // Returns true only when a new override was successfully applied, so the caller can loop back to
    // the "Link which game?" menu; cancelling, clearing, or a failed lookup returns to the main menu.
    private fun link(
        credentials: IgdbCredentials,
        overrides: CoverArtOverrides,
        cache: CoverArtCache,
        game: Game,
    ): Boolean {
        val platforms = platformsOf(game)
        overrides.get(game.title, platforms)?.let {
            terminal.println(gray("Current override: IGDB “${it.igdbName}” (#${it.igdbId})."))
        }
        val question = "IGDB id or slug for “${game.title}” (blank to cancel, 'clear' to remove)"
        val input = terminal.prompt(question)?.trim().orEmpty()
        return when {
            input.isEmpty() -> false

            input.equals(CLEAR, ignoreCase = true) -> {
                clear(overrides, game, platforms)
                false
            }

            else -> apply(credentials, overrides, cache, game, platforms, input)
        }
    }

    private fun clear(overrides: CoverArtOverrides, game: Game, platforms: Set<Platform>) {
        val message = if (overrides.clear(game.title, platforms)) {
            "Removed the override for “${game.title}”."
        } else {
            "No override to remove for “${game.title}”."
        }
        terminal.println(message)
    }

    private fun apply(
        credentials: IgdbCredentials,
        overrides: CoverArtOverrides,
        cache: CoverArtCache,
        game: Game,
        platforms: Set<Platform>,
        idOrSlug: String,
    ): Boolean {
        val httpClient = OkHttpClient()
        try {
            val http = OkHttpCoverArtHttp(httpClient)
            val igdb = IgdbClient(credentials, http)
            val info = runCatching { runBlocking { igdb.fetchGame(idOrSlug) } }.getOrElse { error ->
                terminal.println(brightRed("IGDB lookup failed: ${error.message}"))
                return false
            }
            val imageId = info?.imageId
            return when {
                info == null -> {
                    terminal.println(yellow("No IGDB game found for “$idOrSlug”."))
                    false
                }

                imageId == null -> {
                    terminal.println(yellow("IGDB “${info.name}” has no cover art."))
                    false
                }

                else -> {
                    overrides.set(game.title, platforms, OverrideEntry(info.id, info.name, imageId, info.slug))
                    terminal.println(brightGreen("Linked “${game.title}” → IGDB “${info.name}”."))
                    downloadNow(igdb, overrides, cache, game, http)
                    true
                }
            }
        } finally {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
    }

    private fun downloadNow(
        igdb: IgdbClient,
        overrides: CoverArtOverrides,
        cache: CoverArtCache,
        game: Game,
        http: CoverArtHttp,
    ) {
        runBlocking {
            CoverArtFetcher(igdb, http, cache, overrides, FileSystem.SYSTEM).fetch(listOf(game)) { report(it) }
        }
        cache.save()
    }

    private fun report(result: CoverArtResult) {
        val message = when (result.outcome) {
            CoverArtOutcome.DOWNLOADED -> brightGreen("Saved cover → ${result.detail}.")
            CoverArtOutcome.UP_TO_DATE -> "Cover already up to date (${result.detail})."
            CoverArtOutcome.NO_FOLDER -> yellow("This game has no folder on disk.")
            CoverArtOutcome.FAILED -> brightRed("Download failed: ${result.detail}.")
            CoverArtOutcome.NO_MATCH, CoverArtOutcome.NO_COVER -> yellow("No cover available.")
        }
        terminal.println(message)
    }

    private fun reportMissingConfig() {
        CoverArtConfig.writeTemplate(FileSystem.SYSTEM, library.coverArtConfigFile)
        terminal.println(yellow("IGDB credentials not found."))
        terminal.println("Add your Twitch client_id / client_secret to:")
        terminal.println("  ${gray(library.coverArtConfigFile.toString())}")
        terminal.println("Register an app at ${gray("https://dev.twitch.tv")}, then run this again.")
    }

    private fun gameLabel(game: Game): String {
        val platforms = platformsOf(game).map { it.name }.sorted().joinToString(", ")
        return if (platforms.isEmpty()) game.title else "${game.title}  ·  $platforms"
    }

    private fun platformsOf(game: Game): Set<Platform> =
        game.tracks.orEmpty().map { it.platform }.toSet()

    private companion object {
        const val LIST_ROW = "List games without a cover link"
        const val SEARCH_ROW = "Search by name"
        const val CLEAR = "clear"
        const val CANCEL = "← Cancel"
    }
}
