package net.sigmabeta.chipbox.server.http

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.repository.Repository

/**
 * Bytes-serving endpoints. Every one is keyed by a record id (track / game / artist) — no
 * filesystem paths cross the network. [PublicUrls] is the canonical source for the URL shapes
 * the JSON serialization layer plugs into [Game.photoUrl] / [Artist.photoUrl] / [Track.path] /
 * [ChainFile.uri]; this file is the matching read side.
 *
 *  - `GET /api/files/{trackId}`            — track audio bytes (mapped from `Track.path`)
 *  - `GET /api/games/{id}/cover`           — game cover image (mapped from `Game.photoUrl`)
 *  - `GET /api/artists/{id}/photo`         — artist photo (mapped from `Artist.photoUrl`)
 *  - `GET /api/tracks/{id}/chain/{file}`   — PSF/SSF/USF chain file by basename
 *
 * Internally each endpoint reads the model's still-absolute filesystem path from the DB and
 * dispatches through `contentSources.get(source).openBytes(absolutePath)` — bytes flow out
 * exactly the way the scanner pulled them in, the only difference is that callers never see
 * the absolute path.
 */
internal fun Route.fileRoutes(repository: Repository, contentSources: ContentSourceRegistry) {
    route("/api/files") {
        get("/{trackId}") {
            val trackId = call.parameters["trackId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid trackId"))

            val track = repository.getTrack(trackId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown track $trackId"))

            val source = contentSources.get(track.source)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No content source for '${track.source}'"),
                )

            val bytes = source.openBytes(track.path)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("File missing for track $trackId"),
                )

            val filename = track.path.substringAfterLast('/').substringAfterLast('\\')
            call.attachAs(filename)
            call.respondBytes(bytes, contentType = ContentType.Application.OctetStream)
        }
    }

    gameCoverRoute(repository, contentSources)
    artistPhotoRoute(repository, contentSources)
    chainFileRoute(repository, contentSources)
}

private fun Route.gameCoverRoute(repository: Repository, contentSources: ContentSourceRegistry) {
    route("/api/games") {
        get("/{id}/cover") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid game id"))
            val game = repository.getGame(id, withTracks = false, withArtists = false).firstSettledSingle()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown game $id"))
            val photoUrl = game.photoUrl
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Game $id has no cover"))
            // Game/Artist cover images are scanned out of the filesystem alongside their tracks,
            // so they live under the same `LocalFileContentSource` ("file"). Hardcoded here since
            // the Game/Artist models don't carry a `source` field of their own.
            respondViaContentSource(
                contentSources,
                sourceId = "file",
                path = photoUrl,
                resizeMaxDim = call.imageSizeParam(),
            )
        }
    }
}

private fun Route.artistPhotoRoute(repository: Repository, contentSources: ContentSourceRegistry) {
    route("/api/artists") {
        get("/{id}/photo") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid artist id"))
            val artist = repository.getArtist(id, withTracks = false, withGames = false).firstSettledSingle()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown artist $id"))
            val photoUrl = artist.photoUrl
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Artist $id has no photo"))
            respondViaContentSource(
                contentSources,
                sourceId = "file",
                path = photoUrl,
                resizeMaxDim = call.imageSizeParam(),
            )
        }
    }
}

private fun Route.chainFileRoute(repository: Repository, contentSources: ContentSourceRegistry) {
    route("/api/tracks") {
        get("/{id}/chain/{filename}") {
            val trackId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid track id"))
            val filename = call.parameters["filename"]
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Missing chain filename"))
            val track = repository.getTrack(trackId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown track $trackId"))
            val chain = track.chainFiles.firstOrNull { it.filename == filename }
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Track $trackId has no chain file '$filename'"),
                )
            val source = contentSources.get(track.source)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No content source for '${track.source}'"),
                )
            val bytes = source.openBytes(chain.uri)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Chain file missing: $filename"),
                )
            call.attachAs(filename)
            call.respondBytes(bytes, contentType = ContentType.Application.OctetStream)
        }
    }
}

@Suppress("ReturnCount")
private suspend fun io.ktor.server.routing.RoutingContext.respondViaContentSource(
    contentSources: ContentSourceRegistry,
    sourceId: String,
    path: String,
    resizeMaxDim: Int? = null,
) {
    val source = contentSources.get(sourceId)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No content source for '$sourceId'"))
    val originalBytes = source.openBytes(path)
        ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("File missing"))

    // Image endpoints (game cover, artist photo) — derive Content-Type from the extension and
    // skip Content-Disposition: attachment so the browser / `navigator.mediaSession` artwork
    // fetcher treats the response as a renderable image rather than a download.
    //
    // Cache-Control: covers are addressed by an opaque record-id URL (`/api/games/{id}/cover`)
    // and rarely change; let the browser serve subsequent loads from its HTTP cache without
    // round-tripping. `stale-while-revalidate` lets it return the cached bytes immediately
    // even past max-age while a background refresh runs — keeps the grid snappy when scrolling
    // back through already-seen rows. If a cover does update, it'll propagate within ~1 day.
    call.response.header(HttpHeaders.CacheControl, IMAGE_CACHE_CONTROL)

    if (resizeMaxDim != null) {
        val resized = ImageResizer.resize(originalBytes, resizeMaxDim)
        if (resized != null) {
            val (resizedBytes, mime) = resized
            call.respondBytes(resizedBytes, contentType = ContentType.parse(mime))
            return
        }
        // Resize failed (couldn't decode); fall through to the unresized passthrough below.
    }
    call.respondBytes(originalBytes, contentType = imageContentType(path))
}

/**
 * Parse `?size=<int>` for image endpoints. Returns null when absent or out of range. Clamped to
 * a sane ceiling so a malicious / buggy caller can't ask the server to allocate a gigantic
 * BufferedImage by passing `?size=99999`.
 */
@Suppress("ReturnCount")
private fun io.ktor.server.application.ApplicationCall.imageSizeParam(): Int? {
    val raw = request.queryParameters["size"]?.toIntOrNull() ?: return null
    if (raw <= 0) return null
    return raw.coerceAtMost(MAX_RESIZE_DIM)
}

private const val MAX_RESIZE_DIM = 4096
private const val IMAGE_CACHE_CONTROL =
    "public, max-age=86400, stale-while-revalidate=604800"

private fun imageContentType(path: String): ContentType =
    when (path.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "webp" -> ContentType("image", "webp")
        "svg" -> ContentType.Image.SVG
        else -> ContentType.Application.OctetStream
    }

/** `Content-Disposition: attachment; filename="…"` so browsers don't try to navigate to the bytes. */
private fun io.ktor.server.application.ApplicationCall.attachAs(filename: String) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            filename,
        ).toString(),
    )
}
