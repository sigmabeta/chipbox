package net.sigmabeta.chipbox.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.models.Platform
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Outcome of an IGDB cover-art lookup for one game. */
sealed interface CoverLookup {
    /**
     * A game matched and has cover art with this IGDB [imageId]. The id (not a full URL) is the
     * stable, size-independent identity of the cover; the sized download URL is derived from it via
     * [IgdbClient.coverUrl], so changing the image size doesn't invalidate cached lookups.
     */
    data class Found(val imageId: String) : CoverLookup

    /** A game matched, but it has no cover art on IGDB. */
    data object NoCover : CoverLookup

    /** No game on IGDB matched the searched name. */
    data object NoMatch : CoverLookup
}

/**
 * Minimal IGDB cover-art lookup. Authenticates against Twitch (client-credentials grant), searches
 * the `games` endpoint by name with progressively simplified fallback names, narrows results to the
 * game's platforms when known, and resolves the best match's cover to an image URL. Calls are
 * throttled and retried on transient network failures. Not thread-safe: drive it from a single
 * thread.
 */
class IgdbClient(
    private val credentials: IgdbCredentials,
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenExpiryMs = 0L
    private var lastRequestAtMs = 0L

    fun lookupCover(gameTitle: String, platforms: Set<Platform>): CoverLookup {
        val platformIds = platforms.mapNotNull { IGDB_PLATFORM_IDS[it] }.toSet()
        val match = nameCandidates(gameTitle).firstNotNullOfOrNull { candidate ->
            findGame(candidate, platformIds)
        }
        val imageId = match?.cover?.imageId
        return when {
            match == null -> CoverLookup.NoMatch
            imageId == null -> CoverLookup.NoCover
            else -> CoverLookup.Found(imageId)
        }
    }

    /** The CDN download URL for a cover [imageId] at the configured [IGDB_IMAGE_SIZE]. */
    fun coverUrl(imageId: String): String = "$IGDB_IMAGE_BASE/$IGDB_IMAGE_SIZE/$imageId.jpg"

    /**
     * Looks up a single game directly by its IGDB id (numeric) or slug, for manual cover-art
     * overrides where the user already knows which IGDB game to link to. Returns null if no such
     * game exists.
     */
    fun fetchGame(idOrSlug: String): IgdbGameInfo? {
        val clause = idOrSlug.toLongOrNull()?.let { "id = $it" } ?: "slug = \"${escape(idOrSlug)}\""
        val game = queryWithRetry("fields name,cover.image_id; where $clause;").firstOrNull()
        return game?.let { IgdbGameInfo(it.name, it.cover?.imageId) }
    }

    // Search [candidate] filtered by platform; if that finds nothing, retry unfiltered so a real
    // game isn't lost to a too-strict filter. Returns the best match, or null if none.
    private fun findGame(candidate: String, platformIds: Set<Int>): IgdbGame? {
        var results = queryWithRetry(searchBody(candidate, platformIds))
        if (results.isEmpty() && platformIds.isNotEmpty()) {
            results = queryWithRetry(searchBody(candidate, emptySet()))
        }
        return results.takeIf { it.isNotEmpty() }?.let { bestMatch(it, candidate) }
    }

    private fun searchBody(name: String, platformIds: Set<Int>): String {
        val where = if (platformIds.isEmpty()) "" else " where platforms = (${platformIds.joinToString(",")});"
        return "fields name,cover.image_id; search \"${escape(name)}\";$where limit $SEARCH_LIMIT;"
    }

    /** Exact (case-insensitive) name match if there is one, otherwise the first result. */
    private fun bestMatch(results: List<IgdbGame>, name: String): IgdbGame =
        results.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: results.first()

    private fun queryWithRetry(body: String): List<IgdbGame> {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(RETRY_WAIT_MS)
            rateLimit()
            try {
                return query(body)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("IGDB query failed")
    }

    private fun query(body: String): List<IgdbGame> {
        val request = Request.Builder()
            .url("$IGDB_API_BASE/games")
            .header("Client-ID", credentials.clientId)
            .header("Authorization", "Bearer ${ensureToken()}")
            .post(body.toRequestBody(TEXT_PLAIN))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) throw IOException("IGDB query failed: ${response.code}: $payload")
            return json.decodeFromString(payload)
        }
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun ensureToken(): String {
        val current = cachedToken
        if (current != null && System.currentTimeMillis() < tokenExpiryMs) return current
        return fetchToken()
    }

    private fun fetchToken(): String {
        val body = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .add("grant_type", "client_credentials")
            .build()
        val request = Request.Builder().url(TWITCH_TOKEN_URL).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) throw IOException("Twitch token request failed: ${response.code}: $payload")
            val token = json.decodeFromString<TwitchToken>(payload)
            cachedToken = token.accessToken
            tokenExpiryMs = System.currentTimeMillis() + (token.expiresIn - TOKEN_BUFFER_SECONDS) * MILLIS_PER_SECOND
            return token.accessToken
        }
    }

    /** Throttle to IGDB's rate limit by spacing successive requests at least [RATE_LIMIT_MS] apart. */
    private fun rateLimit() {
        val waitMs = RATE_LIMIT_MS - (System.currentTimeMillis() - lastRequestAtMs)
        if (waitMs > 0) Thread.sleep(waitMs)
        lastRequestAtMs = System.currentTimeMillis()
    }

    /** Progressively simplified search names: as-is, then without parentheticals, then alnum-only. */
    private fun nameCandidates(name: String): List<String> = buildList {
        add(name)
        val withoutParens = name.replace(PARENS_REGEX, "").trim()
        if (withoutParens.isNotEmpty() && withoutParens != name) add(withoutParens)
        val sanitized = name.replace(NON_ALNUM_REGEX, " ").split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (sanitized.isNotEmpty() && sanitized != name) add(sanitized)
    }.distinct()

    private companion object {
        const val TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val IGDB_API_BASE = "https://api.igdb.com/v4"
        const val IGDB_IMAGE_BASE = "https://images.igdb.com/igdb/image/upload"
        const val IGDB_IMAGE_SIZE = "t_cover_big_2x"
        const val SEARCH_LIMIT = 10
        const val MAX_ATTEMPTS = 3
        const val RATE_LIMIT_MS = 300L
        const val RETRY_WAIT_MS = 2_000L
        const val TOKEN_BUFFER_SECONDS = 60L
        const val MILLIS_PER_SECOND = 1_000L
        val TEXT_PLAIN = "text/plain".toMediaType()
        val PARENS_REGEX = Regex("\\s*\\([^)]*\\)")
        val NON_ALNUM_REGEX = Regex("[^a-zA-Z0-9 ]")
        val WHITESPACE_REGEX = Regex("\\s+")

        // Chipbox platforms mapped to IGDB platform IDs (https://api.igdb.com/v4/platforms), used to
        // narrow searches. Platform.OTHER has no IGDB equivalent and is intentionally absent.
        val IGDB_PLATFORM_IDS: Map<Platform, Int> = mapOf(
            Platform.ARCADE to 52,
            Platform.DREAMCAST to 23,
            Platform.GAMEBOY to 33,
            Platform.GAMEBOY_ADVANCE to 24,
            Platform.GENESIS to 29,
            Platform.NES to 18,
            Platform.N64 to 4,
            Platform.NDS to 20,
            Platform.PC to 6,
            Platform.PS2 to 8,
            Platform.PSX to 7,
            Platform.SATURN to 32,
            Platform.SNES to 19,
        )
    }
}

/** A single IGDB game resolved by id/slug: its name and cover image id (null if it has no cover). */
data class IgdbGameInfo(val name: String, val imageId: String?)

@Serializable
private data class TwitchToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private data class IgdbGame(
    val name: String = "",
    val cover: IgdbCover? = null,
)

@Serializable
private data class IgdbCover(
    @SerialName("image_id") val imageId: String? = null,
)
