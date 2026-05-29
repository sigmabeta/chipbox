package net.sigmabeta.chipbox.server.http

import io.ktor.http.encodeURLPath
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track

/**
 * Rewrites every outbound model's filesystem-shaped string fields into opaque
 * `/api/<resource>/<id>/…` URLs. Goals:
 *
 * - The JSON the browser sees never contains the host's filesystem layout (no `/home/<user>/…`
 *   leak, no library folder structure leak).
 * - Clients address content by stable record IDs, not paths — easier to log, cache, and reason
 *   about; trivially correct under filename collisions across folders.
 * - The server's internal model (and the DB rows) keep the original absolute paths so
 *   [ContentSource] lookups during scan + per-track byte serving still resolve directly without
 *   a reverse-mapping step.
 *
 * Used by the JSON-serving routes immediately before [io.ktor.server.response.respond]; the
 * matching endpoints in [fileRoutes] re-resolve the IDs back to records and stream bytes via
 * the registered [ContentSourceRegistry].
 */
internal object PublicUrls {

    fun gameCover(gameId: Long): String = "/api/games/$gameId/cover"

    fun artistPhoto(artistId: Long): String = "/api/artists/$artistId/photo"

    /**
     * Audio bytes for a track — reuses the existing `/api/files/{trackId}` endpoint that's
     * been live since the initial server tier.
     */
    fun trackFile(trackId: Long): String = "/api/files/$trackId"

    fun trackChain(trackId: Long, filename: String): String =
        "/api/tracks/$trackId/chain/${filename.encodeURLPath()}"
}

internal fun Game.withPublicUrls(): Game = copy(
    photoUrl = photoUrl?.let { PublicUrls.gameCover(id) },
    tracks = tracks?.map { it.withPublicUrls() },
    artists = artists?.map { it.withPublicUrls() },
)

internal fun Artist.withPublicUrls(): Artist = copy(
    photoUrl = photoUrl?.let { PublicUrls.artistPhoto(id) },
    games = games?.map { it.withPublicUrls() },
    tracks = tracks?.map { it.withPublicUrls() },
)

internal fun Track.withPublicUrls(): Track = copy(
    path = PublicUrls.trackFile(id),
    game = game?.withPublicUrls(),
    artists = artists?.map { it.withPublicUrls() },
    chainFiles = chainFiles.map { it.withPublicUrl(id) },
)

private fun ChainFile.withPublicUrl(trackId: Long): ChainFile =
    copy(uri = PublicUrls.trackChain(trackId, filename))
