package net.sigmabeta.chipbox.cli

import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.ui.StringProvider

/**
 * Builds the browser's menu tree from a scanned [ChipboxLibrary]. The top-level sections (Games,
 * Artists, Platforms, All tracks) are materialized once; deeper screens — a game's or artist's
 * songs, a platform's games — are queried lazily when the user opens them (each lazy producer
 * bridges the suspend repository call with [runBlocking], since the browser runs on a plain
 * blocking thread). Games reached through a platform reuse [gameNode], so they drill into songs
 * exactly like the top-level Games section.
 */
class LibraryMenu(
    private val library: ChipboxLibrary,
    private val strings: StringProvider = cliStringProvider,
) {
    suspend fun root(): List<MenuNode> = listOf(
        section("Games", library.games().map { gameNode(it) }),
        section("Artists", library.artists().map { artistNode(it) }),
        section("Platforms", library.platforms().sortedBy { platformLabel(it) }.map { platformNode(it) }),
        section("All tracks", library.tracks().map { leaf(it.title) }),
    )

    private fun gameNode(game: Game): MenuNode = MenuNode(game.title) {
        runBlocking { library.songTitlesForGame(game) }.map { leaf(it) }
    }

    private fun artistNode(artist: Artist): MenuNode = MenuNode(artist.name) {
        runBlocking { library.songTitlesForArtist(artist) }.map { leaf(it) }
    }

    private fun platformNode(platform: Platform): MenuNode = MenuNode(platformLabel(platform)) {
        runBlocking { library.gamesForPlatform(platform) }.map { gameNode(it) }
    }

    private fun section(name: String, children: List<MenuNode>): MenuNode =
        MenuNode("$name (${children.size})") { children }

    private fun leaf(label: String): MenuNode = MenuNode(label)

    private fun platformLabel(platform: Platform): String = strings.getString(platform.stringId)
}
