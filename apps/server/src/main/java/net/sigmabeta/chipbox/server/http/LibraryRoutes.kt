package net.sigmabeta.chipbox.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.sigmabeta.chipbox.models.ErrorResponse
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.Repository

/**
 * `/api/{artists,games,tracks,...}` query routes. Each `withX` query flag maps to the same name
 * on the [Repository] call. To prevent back-link cycles (Game.tracks ↔ Track.game etc.) the
 * routes deliberately pass only one level of expansion — e.g. `/api/games?withTracks=true`
 * returns games with tracks, but those tracks' `game` field is null. The nested model's
 * back-link is just dropped — the existing Repository contract already nulls out anything not
 * requested via a `withX` flag, so the contract holds.
 *
 * Every returned model passes through [withPublicUrls] before serialization so the JSON the
 * browser receives contains opaque `/api/…` endpoint URLs instead of the host's filesystem
 * paths — see [PublicUrls] for the full mapping.
 */
internal fun Route.libraryRoutes(repository: Repository) {
    route("/api") {
        artistRoutes(repository)
        gameRoutes(repository)
        trackRoutes(repository)
        platformRoutes(repository)
        searchRoutes(repository)
        randomRoutes(repository)
    }
}

/**
 * Upper bound on one `limit=` page. The browser's pager asks for 100; 500 leaves room for larger
 * clients while keeping a single response from pulling an arbitrarily large library over the wire.
 * A present-but-invalid `limit` (non-numeric, negative, zero, absurdly large) is clamped into
 * `1..MAX_PAGE_LIMIT` instead of falling back to "no limit", so a malformed request can never
 * accidentally stream the whole catalog.
 */
internal const val MAX_PAGE_LIMIT = 500

// Fallbacks for /api/games/recently-added when the client omits the params (it normally sends both).
private const val DEFAULT_RECENTLY_ADDED_LIMIT = 10
private const val DEFAULT_RECENTLY_ADDED_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
private const val DEFAULT_UNPLAYED_LIMIT = 10

private fun Route.randomRoutes(repository: Repository) {
    route("/random") {
        get("/track") {
            val track = repository.getRandomTrack()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No tracks in library"))
            call.respond(track.withPublicUrls())
        }
        get("/game") {
            val game = repository.getRandomGame()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No games in library"))
            call.respond(game.withPublicUrls())
        }
        get("/artist") {
            val artist = repository.getRandomArtist()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No artists in library"))
            call.respond(artist.withPublicUrls())
        }
    }
}

private fun Route.artistRoutes(repository: Repository) {
    route("/artists") {
        get {
            val withTracks = call.boolParam("withTracks")
            val withGames = call.boolParam("withGames")
            // ?ids=1,2,3 resolves just those artists server-side (most-played row); otherwise list all.
            val ids = call.idsParam("ids")
            val artists = if (ids != null) {
                repository.getArtistsByIds(ids, withTracks, withGames)
            } else {
                val paging = call.pagingParams()
                repository.getAllArtists(withTracks, withGames, paging.limit, paging.offset)
            }
            call.respond(artists.firstSettled().map { it.withPublicUrls() })
        }
        get("/{id}") {
            val id = call.longPathParam("id") ?: return@get call.notFound()
            val withTracks = call.boolParam("withTracks")
            val withGames = call.boolParam("withGames")
            val artist = repository.getArtist(id, withTracks, withGames).firstSettledSingle()
                ?: return@get call.notFound()
            call.respond(artist.withPublicUrls())
        }
        get("/{id}/tracks") {
            val id = call.longPathParam("id") ?: return@get call.notFound()
            val withGame = call.boolParam("withGame")
            val withArtists = call.boolParam("withArtists")
            call.respond(repository.getTracksForArtist(id, withGame, withArtists).map { it.withPublicUrls() })
        }
    }
}

private fun Route.gameRoutes(repository: Repository) {
    route("/games") {
        get {
            val withTracks = call.boolParam("withTracks")
            val withArtists = call.boolParam("withArtists")
            // ?ids=1,2,3 resolves just those games server-side (most-played row); otherwise list all.
            val ids = call.idsParam("ids")
            val games = if (ids != null) {
                repository.getGamesByIds(ids, withTracks, withArtists)
            } else {
                val paging = call.pagingParams()
                repository.getAllGames(withTracks, withArtists, paging.limit, paging.offset)
            }
            call.respond(games.firstSettled().map { it.withPublicUrls() })
        }
        get("/recently-added") {
            val limit = call.intParam("limit") ?: DEFAULT_RECENTLY_ADDED_LIMIT
            val withinMs = call.longParam("withinMs") ?: DEFAULT_RECENTLY_ADDED_WINDOW_MS
            call.respond(
                repository.getRecentlyAddedGames(limit, withinMs).firstSettled().map { it.withPublicUrls() }
            )
        }
        get("/unplayed") {
            val limit = call.intParam("limit") ?: DEFAULT_UNPLAYED_LIMIT
            // ?exclude=1,2,3 — the games the client has played (its history lives client-side). Absent
            // / empty means nothing played yet, so every game is eligible.
            val excluded = call.idsParam("exclude") ?: emptyList()
            call.respond(
                repository.getUnplayedGames(excluded, limit).firstSettled().map { it.withPublicUrls() }
            )
        }
        get("/count") { call.respond(repository.getGameCount()) }
        get("/at") {
            val index = call.intParam("index") ?: return@get call.badRequest("`index` required")
            val game = repository.getGameAtIndex(index) ?: return@get call.notFound()
            call.respond(game.withPublicUrls())
        }
        get("/{id}") {
            val id = call.longPathParam("id") ?: return@get call.notFound()
            val withTracks = call.boolParam("withTracks")
            val withArtists = call.boolParam("withArtists")
            val game = repository.getGame(id, withTracks, withArtists).firstSettledSingle()
                ?: return@get call.notFound()
            call.respond(game.withPublicUrls())
        }
        get("/{id}/tracks") {
            val id = call.longPathParam("id") ?: return@get call.notFound()
            val withGame = call.boolParam("withGame")
            val withArtists = call.boolParam("withArtists")
            call.respond(repository.getTracksForGame(id, withGame, withArtists).map { it.withPublicUrls() })
        }
    }
}

private fun Route.trackRoutes(repository: Repository) {
    route("/tracks") {
        get {
            val withGame = call.boolParam("withGame")
            val withArtists = call.boolParam("withArtists")
            // ?ids=1,2,3 resolves just those tracks server-side (recently/most-played rows); the
            // limit/offset paging applies only to the list-all path.
            val ids = call.idsParam("ids")
            val tracks = if (ids != null) {
                repository.getTracksByIds(ids, withGame, withArtists).firstSettled()
            } else {
                val paging = call.pagingParams()
                repository.getAllTracks(withGame, withArtists, paging.limit, paging.offset).firstSettled()
            }
            call.respond(tracks.map { it.withPublicUrls() })
        }
        get("/{id}") {
            val id = call.longPathParam("id") ?: return@get call.notFound()
            val withGame = call.boolParam("withGame")
            val withArtists = call.boolParam("withArtists")
            val track = repository.getTrack(id, withGame, withArtists) ?: return@get call.notFound()
            call.respond(track.withPublicUrls())
        }
    }
}

private fun Route.platformRoutes(repository: Repository) {
    route("/platforms") {
        get { call.respond(repository.getAvailablePlatforms().firstSettled()) }
        get("/{platform}/games") {
            val platform = call.platformPathParam() ?: return@get call.notFound()
            call.respond(repository.getGamesForPlatform(platform).firstSettled().map { it.withPublicUrls() })
        }
        get("/{platform}/tracks") {
            val platform = call.platformPathParam() ?: return@get call.notFound()
            val withGame = call.boolParam("withGame")
            val withArtists = call.boolParam("withArtists")
            call.respond(repository.getTracksForPlatform(platform, withGame, withArtists).map { it.withPublicUrls() })
        }
    }
}

private fun Route.searchRoutes(repository: Repository) {
    route("/search") {
        get("/games") {
            val q = call.queryParam("q") ?: return@get call.badRequest("`q` required")
            call.respond(repository.searchGames(q).firstSettled().map { it.withPublicUrls() })
        }
        get("/tracks") {
            val q = call.queryParam("q") ?: return@get call.badRequest("`q` required")
            call.respond(repository.searchSongs(q).firstSettled().map { it.withPublicUrls() })
        }
        get("/artists") {
            val q = call.queryParam("q") ?: return@get call.badRequest("`q` required")
            call.respond(repository.searchArtists(q).firstSettled().map { it.withPublicUrls() })
        }
        // No `/search/history` routes: recent searches are browser-local user state, so the server
        // stores none of it. An old client's request falls through to the JSON API 404.
    }
}

// --- small request-handling helpers ---

private fun io.ktor.server.application.ApplicationCall.boolParam(name: String): Boolean =
    request.queryParameters[name]?.toBooleanStrictOrNull() ?: false

private fun io.ktor.server.application.ApplicationCall.longPathParam(name: String): Long? =
    parameters[name]?.toLongOrNull()

private fun io.ktor.server.application.ApplicationCall.queryParam(name: String): String? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }

private fun io.ktor.server.application.ApplicationCall.intParam(name: String): Int? =
    request.queryParameters[name]?.toIntOrNull()

/**
 * The `limit`/`offset` window shared by the games, artists, and tracks list endpoints.
 *
 * - Absent `limit` keeps the historical "everything from `offset` onward" behavior (the DAO treats
 *   `null` as an unbounded page).
 * - A present `limit` is clamped to `1..[MAX_PAGE_LIMIT]`. A non-numeric value becomes the cap
 *   rather than "no limit" so a typo'd query cannot return an unbounded list.
 * - `offset` defaults to 0, negatives clamp to 0, and an overflowing value clamps to
 *   `Int.MAX_VALUE` (an empty page, not page one).
 */
private data class Paging(val limit: Int?, val offset: Int)

private fun io.ktor.server.application.ApplicationCall.pagingParams(): Paging {
    val limit = request.queryParameters["limit"]?.let { raw ->
        raw.toLongOrNull()?.coerceIn(1L, MAX_PAGE_LIMIT.toLong())?.toInt() ?: MAX_PAGE_LIMIT
    }
    val offset = request.queryParameters["offset"]
        ?.toLongOrNull()
        ?.coerceIn(0L, Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: 0
    return Paging(limit, offset)
}

private fun io.ktor.server.application.ApplicationCall.longParam(name: String): Long? =
    request.queryParameters[name]?.toLongOrNull()

// Parse a comma-separated id list (e.g. `?ids=1,2,3`). Null when absent or empty, so callers fall
// back to the list-all path.
private fun io.ktor.server.application.ApplicationCall.idsParam(name: String): List<Long>? =
    request.queryParameters[name]
        ?.split(',')
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.takeIf { it.isNotEmpty() }

private fun io.ktor.server.application.ApplicationCall.platformPathParam(): Platform? =
    parameters["platform"]?.let { name -> runCatching { Platform.valueOf(name) }.getOrNull() }

private suspend fun io.ktor.server.application.ApplicationCall.notFound() {
    respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
}

private suspend fun io.ktor.server.application.ApplicationCall.badRequest(message: String) {
    respond(HttpStatusCode.BadRequest, ErrorResponse(message))
}
