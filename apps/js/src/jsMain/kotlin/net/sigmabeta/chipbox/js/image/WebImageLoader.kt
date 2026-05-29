package net.sigmabeta.chipbox.js.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.http.encodeURLParameter
import net.sigmabeta.chipbox.images.api.HatchetCoilLogger
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.logging.Hatchet

/**
 * Coil [ImageLoader] for the browser. Two things have to be wired up by hand on the JS target:
 *
 * 1. **A network fetcher** — the default Coil image loader on JS has no fetcher registered for
 *    http(s) URLs, so without [KtorNetworkFetcherFactory] every image request errors. We share
 *    the same Ktor engine `RemoteRepository` uses (Js, default-configured).
 *
 * 2. **A photoUrl rewriter** — the chipbox scanner stores image identifiers as the absolute
 *    host filesystem path of `folder.jpg` (or sibling) next to each game's audio files. That
 *    path means nothing in a browser, so Coil's network fetcher would 404 against
 *    `http://<origin>/home/.../folder.jpg`. [LocalPathToServerUrlMapper] turns any
 *    non-http(s) string into `<baseUrl>/api/files/by-path?path=…&source=file`, which the
 *    server already serves the bytes for (same endpoint `HttpContentSource` uses for audio).
 */
fun buildWebImageLoader(
    context: PlatformContext,
    baseUrl: String,
    hatchet: Hatchet,
    analytics: Analytics,
): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(LocalPathToServerUrlMapper(baseUrl))
            add(KtorNetworkFetcherFactory(httpClient = { HttpClient(Js) }))
        }
        .logger(HatchetCoilLogger(hatchet, analytics))
        .build()

/**
 * Maps a [String] image identifier that isn't already an http(s) URL into the corresponding
 * `/api/files/by-path?path=…` URL on the server. Leaves http(s) URLs (e.g. the `preview://`
 * fake-data URIs, real external URLs, or anything the scanner ever decides to store directly)
 * untouched so they flow through to Coil's network fetcher as-is.
 *
 * Coil applies the mapping before any fetcher selection, so the network fetcher only ever sees
 * fully-qualified URLs and `data:` / `preview://` strings flow through unchanged.
 */
private class LocalPathToServerUrlMapper(private val baseUrl: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? {
        if (data.startsWith("http://") || data.startsWith("https://")) return null
        if (data.startsWith("data:")) return null
        val encoded = data.encodeURLParameter()
        return "$baseUrl/api/files/by-path?path=$encoded&source=file"
    }
}
