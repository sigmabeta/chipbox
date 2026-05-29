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
 * `/api/files/{trackId}` — looks up the track in the repository, resolves through
 * [ContentSourceRegistry] using `Track.source`, streams the file via `openBytes`. The
 * `PartialContent` plugin (installed globally) handles HTTP Range automatically for
 * `respondBytes`. Content-Type is derived from `Track.extension` with a permissive default;
 * `Content-Disposition: attachment` ensures browsers download rather than navigate.
 *
 * Chiptune files are small (kilobytes to single MB), so loading the full byte array into
 * memory is fine. If we ever serve large vgmstream WAVs / streamed PCM, switch to
 * `respondOutputStream` and stream chunked.
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
                    ErrorResponse("File missing: ${track.path}"),
                )

            // Chiptune extensions aren't well-known to browsers; default to octet-stream so they
            // get treated as opaque downloads. Filename in Content-Disposition is the path's
            // basename, not the (sometimes weird) chain identifier or DB id.
            val filename = track.path.substringAfterLast('/').substringAfterLast('\\')
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    filename,
                ).toString(),
            )
            call.respondBytes(bytes, contentType = ContentType.Application.OctetStream)
        }

        // Path-keyed sibling of `/{trackId}`. The browser's `BaseGenerator` (via Metro) calls
        // `ContentSource.openBytes(track.path)` where `track.path` is whatever the server-side
        // scanner stored — an absolute filesystem path on the host. The browser can't open that
        // directly, so the JS `HttpContentSource` round-trips through this endpoint instead.
        // `source` defaults to "file" (the JVM `LocalFileContentSource.sourceId`); other content
        // sources (Android SAF on a future shared-server build, etc.) can pass `?source=…`.
        get("/by-path") {
            val pathParam = call.request.queryParameters["path"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("`path` query param required"))
            val sourceId = call.request.queryParameters["source"] ?: "file"
            val source = contentSources.get(sourceId)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No content source for '$sourceId'"),
                )
            val bytes = source.openBytes(pathParam)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("File missing: $pathParam"),
                )
            val filename = pathParam.substringAfterLast('/').substringAfterLast('\\')
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    filename,
                ).toString(),
            )
            call.respondBytes(bytes, contentType = ContentType.Application.OctetStream)
        }
    }
}
