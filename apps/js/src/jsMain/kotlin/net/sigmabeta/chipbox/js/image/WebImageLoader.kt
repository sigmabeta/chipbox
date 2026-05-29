package net.sigmabeta.chipbox.js.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import net.sigmabeta.chipbox.images.api.HatchetCoilLogger
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.logging.Hatchet

/**
 * Coil [ImageLoader] for the browser.
 *
 * - **Network fetcher** — Coil 3's default image loader on JS has no fetcher registered for
 *   http(s) URLs; [KtorNetworkFetcherFactory] (using the same Ktor engine `RemoteRepository`
 *   uses) wires one in. Without it every image request errors.
 *
 * - **baseUrl prepender** — the server's JSON serialization layer (see server `PublicUrls`)
 *   already hands the browser opaque endpoint paths like `/api/games/42/cover`.
 *   [ServerUrlMapper] just prepends [baseUrl] so the Ktor fetcher sees a fully-qualified URL.
 *   http(s) and `data:` URLs flow through unchanged (preview fixtures + external covers).
 */
fun buildWebImageLoader(
    context: PlatformContext,
    baseUrl: String,
    hatchet: Hatchet,
    analytics: Analytics,
): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(ServerUrlMapper(baseUrl))
            add(KtorNetworkFetcherFactory(httpClient = { HttpClient(Js) }))
        }
        .logger(HatchetCoilLogger(hatchet, analytics))
        .build()

/**
 * Prepends [baseUrl] to relative server paths (e.g. `/api/games/42/cover`). http(s)/data URLs
 * pass through unchanged so external image URLs and preview fixtures still work.
 */
private class ServerUrlMapper(private val baseUrl: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? {
        if (data.startsWith("http://") || data.startsWith("https://")) return null
        if (data.startsWith("data:")) return null
        return "$baseUrl$data"
    }
}
