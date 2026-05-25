package net.sigmabeta.chipbox.coverart.real

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.coverart.CoverLookup
import net.sigmabeta.chipbox.coverart.IgdbCredentials
import net.sigmabeta.chipbox.coverart.IgdbGameInfo
import net.sigmabeta.chipbox.models.Platform
import okio.IOException

/**
 * Minimal IGDB cover-art lookup. Authenticates against Twitch (client-credentials grant), searches
 * the `games` endpoint by name with progressively simplified fallback names, ranks results to prefer
 * the game's platforms when known, and resolves the best match's cover to an image URL. Calls are
 * throttled and retried on transient network failures. Not thread-safe: drive it from a single
 * coroutine (its token/rate-limit state is plain mutable fields).
 *
 * Talks to the network only through [CoverArtHttp], so the logic stays multiplatform; the OkHttp
 * impl is supplied by the app.
 */
@OptIn(ExperimentalTime::class)
class IgdbClient(
    private val credentials: IgdbCredentials,
    private val http: CoverArtHttp,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenExpiryMs = 0L
    private var lastRequestAtMs = 0L

    suspend fun lookupCover(gameTitle: String, platforms: Set<Platform>): CoverLookup {
        val platformIds = platforms.mapNotNull { IGDB_PLATFORM_IDS[it] }.toSet()
        val match = nameCandidates(gameTitle).firstNotNullOfOrNull { candidate ->
            findGame(candidate, platformIds)
        }
        val imageId = match?.cover?.imageId
        return when {
            match == null -> CoverLookup.NoMatch
            imageId == null -> CoverLookup.NoCover(match.id.toString(), match.name, match.slug)
            else -> CoverLookup.Found(imageId, match.id.toString(), match.name, match.slug)
        }
    }

    /** The CDN download URL for a cover [imageId] at the configured [IGDB_IMAGE_SIZE]. */
    fun coverUrl(imageId: String): String = "$IGDB_IMAGE_BASE/$IGDB_IMAGE_SIZE/$imageId.jpg"

    /**
     * Looks up a single game directly by its IGDB id (numeric) or slug, for manual cover-art
     * overrides where the user already knows which IGDB game to link to. Returns null if no such
     * game exists.
     */
    suspend fun fetchGame(idOrSlug: String): IgdbGameInfo? {
        val clause = idOrSlug.toLongOrNull()?.let { "id = $it" } ?: "slug = \"${escape(idOrSlug)}\""
        val game = queryWithRetry("fields id,name,slug,cover.image_id; where $clause;").firstOrNull()
        return game?.let { IgdbGameInfo(it.id.toString(), it.name, it.slug, it.cover?.imageId) }
    }

    // Search [candidate] across all platforms, then rank to prefer the game's own platforms.
    // Returns the best match, or null if none.
    private suspend fun findGame(candidate: String, platformIds: Set<Int>): IgdbGame? =
        queryWithRetry(searchBody(candidate)).takeIf { it.isNotEmpty() }?.let { bestMatch(it, candidate, platformIds) }

    private fun searchBody(name: String): String =
        "fields id,name,slug,cover.image_id,platforms; search \"${escape(name)}\"; limit $SEARCH_LIMIT;"

    /**
     * Ranks candidates platform-first: most overlap with the game's [platformIds] wins, then an exact
     * (case-insensitive) name match, then IGDB's own relevance order (preserved by the stable sort).
     */
    private fun bestMatch(results: List<IgdbGame>, name: String, platformIds: Set<Int>): IgdbGame =
        results.sortedWith(
            compareByDescending<IgdbGame> { it.platforms.count(platformIds::contains) }
                .thenByDescending { it.name.equals(name, ignoreCase = true) },
        ).first()

    private suspend fun queryWithRetry(body: String): List<IgdbGame> {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_WAIT_MS)
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
        val headers = mapOf(
            "Client-ID" to credentials.clientId,
            "Authorization" to "Bearer ${ensureToken()}",
        )
        val payload = http.post("$IGDB_API_BASE/games", headers, TEXT_PLAIN, body)
        return json.decodeFromString(payload)
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun ensureToken(): String {
        val current = cachedToken
        if (current != null && Clock.System.now().toEpochMilliseconds() < tokenExpiryMs) return current
        return fetchToken()
    }

    private fun fetchToken(): String {
        val payload = http.postForm(
            TWITCH_TOKEN_URL,
            mapOf(
                "client_id" to credentials.clientId,
                "client_secret" to credentials.clientSecret,
                "grant_type" to "client_credentials",
            ),
        )
        val token = json.decodeFromString<TwitchToken>(payload)
        cachedToken = token.accessToken
        tokenExpiryMs = Clock.System.now().toEpochMilliseconds() +
            (token.expiresIn - TOKEN_BUFFER_SECONDS) * MILLIS_PER_SECOND
        return token.accessToken
    }

    /** Throttle to IGDB's rate limit by spacing successive requests at least [RATE_LIMIT_MS] apart. */
    private suspend fun rateLimit() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val waitMs = RATE_LIMIT_MS - (nowMs - lastRequestAtMs)
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = Clock.System.now().toEpochMilliseconds()
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
        const val TEXT_PLAIN = "text/plain"

        // Pulled wider than we need: without a platform `where` filter, platform-correct games must
        // survive in the relevance-ranked page before bestMatch can rank them to the top.
        const val SEARCH_LIMIT = 20
        const val MAX_ATTEMPTS = 3
        const val RATE_LIMIT_MS = 300L
        const val RETRY_WAIT_MS = 2_000L
        const val TOKEN_BUFFER_SECONDS = 60L
        const val MILLIS_PER_SECOND = 1_000L
        val PARENS_REGEX = Regex("\\s*\\([^)]*\\)")
        val NON_ALNUM_REGEX = Regex("[^a-zA-Z0-9 ]")
        val WHITESPACE_REGEX = Regex("\\s+")

        // Chipbox platforms mapped to IGDB platform IDs (https://api.igdb.com/v4/platforms), used to
        // rank results by platform. Platform.OTHER has no IGDB equivalent and is intentionally absent.
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

@Serializable
private data class TwitchToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private data class IgdbGame(
    val id: Long = 0,
    val name: String = "",
    val slug: String = "",
    val cover: IgdbCover? = null,
    val platforms: List<Int> = emptyList(),
)

@Serializable
private data class IgdbCover(
    @SerialName("image_id") val imageId: String? = null,
)
