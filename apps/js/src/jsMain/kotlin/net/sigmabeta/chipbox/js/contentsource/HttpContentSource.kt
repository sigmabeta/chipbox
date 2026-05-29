package net.sigmabeta.chipbox.js.contentsource

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.js.repository.RemoteRepository

/**
 * Browser-side [ContentSource] backed by the chipbox-server. `openBytes(path)` round-trips
 * through the server's `/api/files/by-path?path=…&source=…` endpoint — the path the
 * `BaseGenerator` hands us is whatever the server-side scanner stored (an absolute filesystem
 * path on the host), and the server resolves it back into bytes via the same
 * `LocalFileContentSource` it used during the scan.
 *
 * [sourceId] mirrors the JVM `LocalFileContentSource`'s value (`"file"`) so the
 * `ContentSourceRegistry` lookup the generator performs (`registry.get(track.source)`)
 * finds this instance for any track scanned by the desktop/server `apps/server`.
 */
class HttpContentSource(
    private val client: HttpClient,
    private val baseUrl: String = RemoteRepository.resolveBaseUrl(),
    override val sourceId: String = "file",
) : ContentSource {

    override suspend fun openBytes(identifier: String): ByteArray? {
        val response: HttpResponse = client.get("$baseUrl/api/files/by-path") {
            parameter("path", identifier)
            parameter("source", sourceId)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.bodyAsBytes()
    }
}
