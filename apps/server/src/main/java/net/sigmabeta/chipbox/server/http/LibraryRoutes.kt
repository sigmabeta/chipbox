package net.sigmabeta.chipbox.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
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

// Fallbacks for /api/games/recently-added when the client omits the params (it normally sends both).
private const val DEFAULT_RECENTLY_ADDED_LIMIT = 10
private const val DEFAULT_RECENTLY_ADDED_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

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
                repository.getAllArtists(withTracks, withGames)
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
                repository.getAllGames(withTracks, withArtists)
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
                val limit = call.intParam("limit")
                val offset = call.intParam("offset") ?: 0
                repository.getAllTracks(withGame, withArtists, limit, offset).firstSettled()
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
        route("/history") {
            get { call.respond(repository.getSearchHistory().firstSettled()) }
            post {
                val body = call.receive<SearchHistoryAdd>()
                repository.addSearchHistory(body.query)
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}") {
                val id = call.longPathParam("id") ?: return@delete call.notFound()
                repository.removeSearchHistory(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

@Serializable
private data class SearchHistoryAdd(val query: String)

// --- small request-handling helpers ---

private fun io.ktor.server.application.ApplicationCall.boolParam(name: String): Boolean =
    request.queryParameters[name]?.toBooleanStrictOrNull() ?: false

private fun io.ktor.server.application.ApplicationCall.longPathParam(name: String): Long? =
    parameters[name]?.toLongOrNull()

private fun io.ktor.server.application.ApplicationCall.queryParam(name: String): String? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }

private fun io.ktor.server.application.ApplicationCall.intParam(name: String): Int? =
    request.queryParameters[name]?.toIntOrNull()

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

@Serializable
internal data class ErrorResponse(val error: String)
