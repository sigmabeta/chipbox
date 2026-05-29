package net.sigmabeta.chipbox.js.contentsource

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.js.repository.RemoteRepository

/**
 * Browser-side [ContentSource] backed by chipbox-server. The server's JSON serialization layer
 * rewrites `Track.path` / `ChainFile.uri` into opaque `/api/files/{trackId}` /
 * `/api/tracks/{trackId}/chain/{filename}` endpoint URLs (see server `PublicUrls`), so
 * [openBytes] just prepends [baseUrl] and fetches — no path parameter, no source parameter,
 * nothing about the host's filesystem ever appears.
 *
 * [sourceId] still mirrors the JVM `LocalFileContentSource`'s value (`"file"`) so the
 * `ContentSourceRegistry` dispatch the generator performs (`registry.get(track.source)`)
 * finds this instance for any track scanned by the server.
 */
class HttpContentSource(
    private val client: HttpClient,
    private val baseUrl: String = RemoteRepository.resolveBaseUrl(),
    override val sourceId: String = "file",
) : ContentSource {

    override suspend fun openBytes(identifier: String): ByteArray? {
        val response: HttpResponse = client.get("$baseUrl$identifier")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.bodyAsBytes()
    }
}
