package net.sigmabeta.chipbox.server.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * `/api/games` and `/api/artists` must page like `/api/tracks`: `limit`/`offset` select a window,
 * `ids` bypasses paging entirely, and walking every page recovers the whole catalog exactly once.
 *
 * The fixtures deliberately give many rows the same sort key (`COLLATE NOCASE` makes "Same"/"same"
 * peers). SQLite does not define an order among peers, so a page boundary through a tie group is
 * the case that duplicates or skips rows when the ordering has no stable tiebreaker.
 */
class LibraryPagingTest {

    @Test
    fun `game pages partition the catalog across duplicate titles`() {
        withLibrary(games = duplicateTitleGames(12)) { repo ->
            testApplication {
                configureJson { libraryRoutes(repo) }

                val full = itemIds(client, "/api/games")
                val walked = walkPages(pageSize = 5) { offset -> itemIds(client, "/api/games?limit=5&offset=$offset") }

                assertEquals(full, walked, "walking pages must reproduce the full catalog in order")
                assertEquals(full.size, walked.toSet().size, "no game may appear on two pages")
            }
        }
    }

    @Test
    fun `artist pages partition the catalog across duplicate names`() {
        withLibrary(artists = duplicateNameArtists(11)) { repo ->
            testApplication {
                configureJson { libraryRoutes(repo) }

                val full = itemIds(client, "/api/artists")
                val walked = walkPages(pageSize = 4) { offset ->
                    itemIds(client, "/api/artists?limit=4&offset=$offset")
                }

                assertEquals(full, walked, "walking pages must reproduce the full catalog in order")
                assertEquals(full.size, walked.toSet().size, "no artist may appear on two pages")
            }
        }
    }

    @Test
    fun `ids bypass paging for games and artists`() = withLibrary(
        games = duplicateTitleGames(6),
        artists = duplicateNameArtists(6),
    ) { repo ->
        testApplication {
            configureJson { libraryRoutes(repo) }

            val gameIds = itemIds(client, "/api/games").take(3)
            val artists = itemIds(client, "/api/artists").take(3)

            assertEquals(
                gameIds,
                itemIds(client, "/api/games?limit=1&ids=${gameIds.joinToString(",")}"),
                "?ids must resolve every requested game even when limit=1",
            )
            assertEquals(
                artists,
                itemIds(client, "/api/artists?limit=1&ids=${artists.joinToString(",")}"),
                "?ids must resolve every requested artist even when limit=1",
            )
        }
    }

    @Test
    fun `track paging is unchanged`() = withLibrary(tracks = duplicateTitleTracks(9)) { repo ->
        testApplication {
            configureJson { libraryRoutes(repo) }

            val full = itemIds(client, "/api/tracks")
            val walked = walkPages(pageSize = 4) { offset -> itemIds(client, "/api/tracks?limit=4&offset=$offset") }

            assertEquals(full, walked, "tracks must still page with limit/offset")
            assertEquals(full.size, walked.toSet().size, "no track may appear on two pages")

            val requested = full.take(3)
            assertEquals(
                requested,
                itemIds(client, "/api/tracks?limit=1&ids=${requested.joinToString(",")}"),
                "?ids must keep bypassing track paging",
            )
        }
    }

    @Test
    fun `absent limit returns the whole catalog`() = withLibrary(games = duplicateTitleGames(12)) { repo ->
        testApplication {
            configureJson { libraryRoutes(repo) }

            assertEquals(12, itemIds(client, "/api/games").size)
        }
    }

    @Test
    fun `limit is clamped to the page cap`() = withLibrary(games = duplicateTitleGames(MAX_PAGE_LIMIT + 1)) { repo ->
        testApplication {
            configureJson { libraryRoutes(repo) }

            assertEquals(MAX_PAGE_LIMIT, itemIds(client, "/api/games?limit=100000").size)
        }
    }

    @Test
    fun `negative limit clamps to one row instead of meaning no limit`() {
        withLibrary(games = duplicateTitleGames(12)) { repo ->
            testApplication {
                configureJson { libraryRoutes(repo) }

                assertEquals(1, itemIds(client, "/api/games?limit=-1").size)
            }
        }
    }

    @Test
    fun `malformed limit is capped instead of unbounded`() {
        withLibrary(games = duplicateTitleGames(MAX_PAGE_LIMIT + 1)) { repo ->
            testApplication {
                configureJson { libraryRoutes(repo) }

                assertEquals(MAX_PAGE_LIMIT, itemIds(client, "/api/games?limit=lots").size)
            }
        }
    }

    @Test
    fun `negative offset clamps to the first page`() = withLibrary(games = duplicateTitleGames(12)) { repo ->
        testApplication {
            configureJson { libraryRoutes(repo) }

            assertEquals(
                itemIds(client, "/api/games").take(3),
                itemIds(client, "/api/games?limit=3&offset=-7"),
            )
        }
    }

    @Test
    fun `overflowing offset returns an empty page instead of page one`() {
        withLibrary(games = duplicateTitleGames(12)) { repo ->
            testApplication {
                configureJson { libraryRoutes(repo) }

                assertEquals(emptyList(), itemIds(client, "/api/games?offset=999999999999"))
            }
        }
    }

    // ---- fixtures ----

    private fun duplicateTitleGames(count: Int): List<GameEntity> = (0 until count).map { index ->
        GameEntity(
            title = if (index % 3 == 0) "Same" else "same",
            photoUrl = null,
            folderKey = "/library/game-$index",
            folderSignature = "signature-$index",
        )
    }

    private fun duplicateNameArtists(count: Int): List<ArtistEntity> = (0 until count).map { index ->
        ArtistEntity(name = if (index % 2 == 0) "Same Artist" else "same artist")
    }

    private fun duplicateTitleTracks(count: Int): List<TrackEntity> = (0 until count).map { index ->
        TrackEntity(
            title = if (index % 3 == 0) "Same Song" else "same song",
            path = "/library/track-$index.spc",
            source = "file",
            trackLengthMs = 1_000,
            trackNumber = 0,
            fadeLengthMs = 0,
            gameId = 1,
        )
    }
}

/** Bounded so a server that ignores `offset` fails an assertion instead of looping forever. */
private const val MAX_PAGES = 100

private suspend fun walkPages(pageSize: Int, page: suspend (offset: Int) -> List<Long>): List<Long> {
    val seen = mutableListOf<Long>()
    repeat(MAX_PAGES) {
        val ids = page(seen.size)
        if (ids.isEmpty()) return seen
        seen += ids
    }
    error("paging did not terminate after $MAX_PAGES pages; got ${seen.size} ids (page size $pageSize)")
}

private suspend fun itemIds(client: HttpClient, path: String): List<Long> =
    Json.parseToJsonElement(client.get(path).bodyAsText())
        .jsonArray
        .map { it.jsonObject.getValue("id").jsonPrimitive.long }

/** Runs [block] against the JSON routes over a freshly seeded in-memory library. */
private fun withLibrary(
    games: List<GameEntity> = emptyList(),
    artists: List<ArtistEntity> = emptyList(),
    tracks: List<TrackEntity> = emptyList(),
    block: suspend (Repository) -> Unit,
) = runBlocking {
    val db = FakeDatabase()
    games.forEach { db.gameDao.insert(it) }
    artists.forEach { db.artistDao.insert(it) }
    tracks.forEach { db.trackDao.insert(it) }
    val repository = DatabaseRepository(
        artistDao = db.artistDao,
        gameDao = db.gameDao,
        trackDao = db.trackDao,
        gameArtistDao = db.gameArtistDao,
        trackArtistDao = db.trackArtistDao,
        searchHistoryDao = db.searchHistoryDao,
        hatchet = BluntHatchet(),
    )
    block(repository)
}

/** Minimal application with the JSON routes under test — mirrors the real wiring in Main.kt. */
private fun ApplicationTestBuilder.configureJson(routes: Routing.() -> Unit) {
    application {
        install(ContentNegotiation) { json() }
        routing(routes)
    }
}
