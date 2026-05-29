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
    }
}

private fun Route.artistRoutes(repository: Repository) {
    route("/artists") {
        get {
            val withTracks = call.boolParam("withTracks")
            val withGames = call.boolParam("withGames")
            call.respond(repository.getAllArtists(withTracks, withGames).firstSettled().map { it.withPublicUrls() })
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
            call.respond(repository.getAllGames(withTracks, withArtists).firstSettled().map { it.withPublicUrls() })
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
            call.respond(repository.getAllTracks(withGame, withArtists).firstSettled().map { it.withPublicUrls() })
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
