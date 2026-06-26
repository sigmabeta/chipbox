package net.sigmabeta.chipbox.js.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository

/**
 * Browser-side [Repository] that fans every read call out to the chipbox-server JSON API. Base
 * URL is `window.location.origin` so the same bundle works both when served from the server
 * itself (single-deployment, same-origin) and when run from the webpack devserver (where we hit
 * the configured `CHIPBOX_API_BASE` instead — see [resolveBaseUrl]).
 *
 * Each `Flow<Data<T>>` returning method launches a single HTTP request and emits
 * `Data.Loading` then `Data.Succeeded(...)` (or `Data.Empty` / `Data.Failed`). No polling — one
 * shot per subscription, matching the server's single-snapshot semantics.
 *
 * Write paths (`upsertGame`, `pruneGames`, `clearLibrary`, `folderSnapshots`) are scanner-side
 * concerns the UI doesn't drive; they error if called.
 */
class RemoteRepository(
    private val client: HttpClient,
    private val baseUrl: String = resolveBaseUrl(),
) : Repository {

    // ---------- lists ----------

    override fun getAllArtists(
        withTracks: Boolean,
        withGames: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Artist>>> =
        listFlow {
            client.get("$baseUrl/api/artists") {
                parameter("withTracks", withTracks)
                parameter("withGames", withGames)
                if (limit != null) parameter("limit", limit)
                if (offset != 0) parameter("offset", offset)
            }.body()
        }

    override fun getAllGames(
        withTracks: Boolean,
        withArtists: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Game>>> =
        listFlow {
            client.get("$baseUrl/api/games") {
                parameter("withTracks", withTracks)
                parameter("withArtists", withArtists)
                if (limit != null) parameter("limit", limit)
                if (offset != 0) parameter("offset", offset)
            }.body()
        }

    override fun getAllTracks(
        withGame: Boolean,
        withArtists: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Track>>> =
        listFlow {
            client.get("$baseUrl/api/tracks") {
                parameter("withGame", withGame)
                parameter("withArtists", withArtists)
                if (limit != null) parameter("limit", limit)
                if (offset != 0) parameter("offset", offset)
            }.body()
        }

    // No server-side id filter yet, so fetch and filter client-side. Non-regressive: the callers
    // that now use this previously fetched the full track list here anyway.
    override fun getTracksByIds(ids: List<Long>, withGame: Boolean, withArtists: Boolean): Flow<Data<List<Track>>> =
        listFlow {
            val idSet = ids.toSet()
            client.get("$baseUrl/api/tracks") {
                parameter("withGame", withGame)
                parameter("withArtists", withArtists)
            }.body<List<Track>>().filter { it.id in idSet }
        }

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        client.get("$baseUrl/api/games/$id/tracks") {
            parameter("withGame", withGame)
            parameter("withArtists", withArtists)
        }.body()

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        client.get("$baseUrl/api/artists/$id/tracks") {
            parameter("withGame", withGame)
            parameter("withArtists", withArtists)
        }.body()

    override suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean,
    ): List<Track> = client.get("$baseUrl/api/platforms/${platform.name}/tracks") {
        parameter("withGame", withGame)
        parameter("withArtists", withArtists)
    }.body()

    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/platforms/${platform.name}/games").body()
    }

    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> = listFlow {
        client.get("$baseUrl/api/platforms").body()
    }

    // Surgical server-side query — the window filter, random pick, and LIMIT all run in SQL, so the
    // Home row never pulls the full catalog over HTTP.
    override fun getRecentlyAddedGames(limit: Int, withinMs: Long): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/games/recently-added") {
            parameter("limit", limit)
            parameter("withinMs", withinMs)
        }.body()
    }

    // ---------- single ----------

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean): Flow<Data<Game?>> =
        singleFlow {
            val response = client.get("$baseUrl/api/games/$id") {
                parameter("withTracks", withTracks)
                parameter("withArtists", withArtists)
            }
            response.bodyOrNull<Game>()
        }

    override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean): Flow<Data<Artist?>> =
        singleFlow {
            val response = client.get("$baseUrl/api/artists/$id") {
                parameter("withTracks", withTracks)
                parameter("withGames", withGames)
            }
            response.bodyOrNull<Artist>()
        }

    override suspend fun getTrack(id: Long, withGame: Boolean, withArtists: Boolean): Track? {
        val response = client.get("$baseUrl/api/tracks/$id") {
            parameter("withGame", withGame)
            parameter("withArtists", withArtists)
        }
        return response.bodyOrNull<Track>()
    }

    // ---------- random ----------
    // Server-side `ORDER BY RANDOM() LIMIT 1` — single round trip, returns just the one item.
    // The earlier in-app `getAllX().randomOrNull()` chain would pull the entire catalog over
    // HTTP on every RNG-button click, which timed out on libraries past a few hundred tracks.

    override suspend fun getRandomTrack(): Track? =
        client.get("$baseUrl/api/random/track").bodyOrNull()

    override suspend fun getRandomGame(): Game? =
        client.get("$baseUrl/api/random/game").bodyOrNull()

    override suspend fun getRandomArtist(): Artist? =
        client.get("$baseUrl/api/random/artist").bodyOrNull()

    // ---------- search ----------

    override fun searchGames(query: String): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/search/games") { parameter("q", query) }.body()
    }

    override fun searchSongs(query: String): Flow<Data<List<Track>>> = listFlow {
        client.get("$baseUrl/api/search/tracks") { parameter("q", query) }.body()
    }

    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = listFlow {
        client.get("$baseUrl/api/search/artists") { parameter("q", query) }.body()
    }

    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = listFlow {
        client.get("$baseUrl/api/search/history").body()
    }

    override suspend fun addSearchHistory(query: String) {
        client.post("$baseUrl/api/search/history") {
            contentType(ContentType.Application.Json)
            setBody(SearchHistoryAddBody(query))
        }
    }

    override suspend fun removeSearchHistory(id: Long) {
        client.delete("$baseUrl/api/search/history/$id")
    }

    // ---------- scanner-side; the UI doesn't drive these ----------

    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> =
        error("Server-side only — RemoteRepository can't enumerate folder snapshots.")

    override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome =
        error("Server-side only — scans run on the server, not in the browser.")

    override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> =
        error("Server-side only — scans run on the server, not in the browser.")

    override suspend fun clearLibrary() =
        error("Server-side only — clearing the library is a server admin action.")

    // ---------- helpers ----------

    private fun <T> listFlow(call: suspend () -> List<T>): Flow<Data<List<T>>> = flow {
        emit(Data.Loading)
        try {
            val list = call()
            emit(if (list.isEmpty()) Data.Empty else Data.Succeeded(list))
        } catch (ex: Throwable) {
            emit(Data.Failed(ex.message ?: ex::class.simpleName ?: "Request failed"))
        }
    }

    private fun <T : Any> singleFlow(call: suspend () -> T?): Flow<Data<T?>> = flow {
        emit(Data.Loading)
        try {
            val item = call()
            emit(if (item == null) Data.Empty else Data.Succeeded(item))
        } catch (ex: Throwable) {
            emit(Data.Failed(ex.message ?: ex::class.simpleName ?: "Request failed"))
        }
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrNull(): T? =
        if (status == HttpStatusCode.NotFound) null else body()

    @Serializable
    private data class SearchHistoryAddBody(val query: String)

    companion object {
        /**
         * In production the server hosts the bundle off the same origin, so `window.location.origin`
         * is correct. In dev (webpack-devserver on :8081 hitting the server on :8080) the bundle
         * runs at a different origin; CORS on the server handles the cross-origin call. Override
         * via a `?api=http://other-host:8080` URL param if needed.
         */
        fun resolveBaseUrl(): String {
            val override = window.location.search
                .removePrefix("?")
                .split('&')
                .firstOrNull { it.startsWith("api=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotBlank() }
            if (override != null) return override.trimEnd('/')

            val origin = window.location.origin
            // Dev convenience: webpack devserver default port is 8081; assume the API server is
            // on :8080 of the same host. In prod this branch never fires (same-origin).
            return if (origin.endsWith(":8081")) {
                origin.removeSuffix(":8081") + ":8080"
            } else {
                origin
            }
        }
    }
}
