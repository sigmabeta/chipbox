package net.sigmabeta.chipbox.js.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.HealthStatus
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.ScanRetryPolicy
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.models.apiErrorMessage
import net.sigmabeta.chipbox.models.readListAwaitingScan
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
 * `Data.Loading` then `Data.Succeeded(...)` (or `Data.Empty` / `Data.Failed`). List reads await the
 * server's startup scan — while `/api/health` says a scan is running and the read is empty, the
 * read is retried under a bounded [ScanRetryPolicy] so a browser opened during the first scan still
 * lands on the library once the scan completes. Search reads do not wait (an empty result is a
 * legitimate answer, not a sign the scan has not finished).
 *
 * Write paths (`upsertGame`, `pruneGames`, `clearLibrary`, `folderSnapshots`) are scanner-side
 * concerns the UI doesn't drive; they error if called.
 */
class RemoteRepository(
    private val client: HttpClient,
    private val baseUrl: String = resolveBaseUrl(),
    private val scanRetryPolicy: ScanRetryPolicy = ScanRetryPolicy(),
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
            }.bodyOrThrow()
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
            }.bodyOrThrow()
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
            }.bodyOrThrow()
        }

    // Server-side id filter (`?ids=…`) so only the requested rows come over the wire, not the
    // whole list.
    override fun getTracksByIds(ids: List<Long>, withGame: Boolean, withArtists: Boolean): Flow<Data<List<Track>>> =
        listFlow {
            client.get("$baseUrl/api/tracks") {
                parameter("ids", ids.joinToString(","))
                parameter("withGame", withGame)
                parameter("withArtists", withArtists)
            }.bodyOrThrow()
        }

    override fun getGamesByIds(ids: List<Long>, withTracks: Boolean, withArtists: Boolean): Flow<Data<List<Game>>> =
        listFlow {
            client.get("$baseUrl/api/games") {
                parameter("ids", ids.joinToString(","))
                parameter("withTracks", withTracks)
                parameter("withArtists", withArtists)
            }.bodyOrThrow()
        }

    override fun getArtistsByIds(ids: List<Long>, withTracks: Boolean, withGames: Boolean): Flow<Data<List<Artist>>> =
        listFlow {
            client.get("$baseUrl/api/artists") {
                parameter("ids", ids.joinToString(","))
                parameter("withTracks", withTracks)
                parameter("withGames", withGames)
            }.bodyOrThrow()
        }

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        client.get("$baseUrl/api/games/$id/tracks") {
            parameter("withGame", withGame)
            parameter("withArtists", withArtists)
        }.bodyOrThrow()

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
        client.get("$baseUrl/api/artists/$id/tracks") {
            parameter("withGame", withGame)
            parameter("withArtists", withArtists)
        }.bodyOrThrow()

    override suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean,
    ): List<Track> = client.get("$baseUrl/api/platforms/${platform.name}/tracks") {
        parameter("withGame", withGame)
        parameter("withArtists", withArtists)
    }.bodyOrThrow()

    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/platforms/${platform.name}/games").bodyOrThrow()
    }

    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> = listFlow {
        client.get("$baseUrl/api/platforms").bodyOrThrow()
    }

    // Surgical server-side query — the window filter, random pick, and LIMIT all run in SQL, so the
    // Home row never pulls the full catalog over HTTP.
    override fun getRecentlyAddedGames(limit: Int, withinMs: Long): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/games/recently-added") {
            parameter("limit", limit)
            parameter("withinMs", withinMs)
        }.bodyOrThrow()
    }

    // The played-game ids come from the browser's local history; the server excludes them, picks at
    // random, and applies the LIMIT in SQL, so the "New to You" row stays surgical over HTTP.
    override fun getUnplayedGames(playedGameIds: List<Long>, limit: Int): Flow<Data<List<Game>>> = listFlow {
        client.get("$baseUrl/api/games/unplayed") {
            parameter("limit", limit)
            parameter("exclude", playedGameIds.joinToString(","))
        }.bodyOrThrow()
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

    // Server-side count + offset pick — keeps the date-seeded "game of the day" off the
    // fetch-everything path even over HTTP.
    override suspend fun getGameCount(): Int =
        client.get("$baseUrl/api/games/count").bodyOrThrow()

    override suspend fun getGameAtIndex(index: Int): Game? =
        client.get("$baseUrl/api/games/at") { parameter("index", index) }.bodyOrNull()

    // ---------- search ----------

    override fun searchGames(query: String): Flow<Data<List<Game>>> = listFlow(awaitStartupScan = false) {
        client.get("$baseUrl/api/search/games") { parameter("q", query) }.bodyOrThrow()
    }

    override fun searchSongs(query: String): Flow<Data<List<Track>>> = listFlow(awaitStartupScan = false) {
        client.get("$baseUrl/api/search/tracks") { parameter("q", query) }.bodyOrThrow()
    }

    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = listFlow(awaitStartupScan = false) {
        client.get("$baseUrl/api/search/artists") { parameter("q", query) }.bodyOrThrow()
    }

    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = listFlow(awaitStartupScan = false) {
        client.get("$baseUrl/api/search/history").bodyOrThrow()
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

    private fun <T> listFlow(
        awaitStartupScan: Boolean = true,
        call: suspend () -> List<T>,
    ): Flow<Data<List<T>>> = flow {
        emit(Data.Loading)
        try {
            val list = if (awaitStartupScan) {
                readListAwaitingScan(scanRetryPolicy, read = call, readHealth = ::readHealth)
            } else {
                call()
            }
            emit(if (list.isEmpty()) Data.Empty else Data.Succeeded(list))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ex: Throwable) {
            emit(Data.Failed(ex.message ?: ex::class.simpleName ?: "Request failed"))
        }
    }

    /**
     * `/api/health` is advisory: a network error or a malformed body just means "stop waiting" —
     * the list read still decides between Empty and Failed, and a forever-retry here would keep a
     * screen loading against a server that is gone.
     */
    private suspend fun readHealth(): HealthStatus? = try {
        client.get("$baseUrl/api/health").body<HealthStatus>()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private fun <T : Any> singleFlow(call: suspend () -> T?): Flow<Data<T?>> = flow {
        emit(Data.Loading)
        try {
            val item = call()
            emit(if (item == null) Data.Empty else Data.Succeeded(item))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ex: Throwable) {
            emit(Data.Failed(ex.message ?: ex::class.simpleName ?: "Request failed"))
        }
    }

    /**
     * Decode a JSON success; fail with the server's own message otherwise.
     *
     * Status and content type are checked before decoding so an unknown `/api` path (now a JSON
     * 404) or any non-JSON page (a proxy error, an old server's SPA fallback) becomes a real
     * message instead of a `JsonDecodingException`. The body is only read on the failure path:
     * a successful response is still decoded by Ktor's configured content-negotiation decoder.
     */
    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        val content = contentType()?.toString()
        if (status.value !in 200..299) {
            val body = try {
                bodyAsText()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            throw ApiResponseException(
                apiErrorMessage(status.value, content, body) ?: "HTTP ${status.value}",
            )
        }
        apiErrorMessage(status.value, content, body = null)?.let { throw ApiResponseException(it) }
        return body()
    }

    /** A missing single resource is `Data.Empty` (404); every other failure keeps its message. */
    private suspend inline fun <reified T> HttpResponse.bodyOrNull(): T? =
        if (status == HttpStatusCode.NotFound) null else bodyOrThrow()

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

/**
 * A non-2xx (or non-JSON success) API response. Its message is already user-facing — it comes from
 * the server's standard `{"error": …}` body when there is one — so flows can emit it verbatim as
 * `Data.Failed`.
 */
private class ApiResponseException(message: String) : Exception(message)
