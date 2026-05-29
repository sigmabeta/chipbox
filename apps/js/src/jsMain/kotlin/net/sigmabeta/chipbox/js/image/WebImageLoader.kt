package net.sigmabeta.chipbox.js.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import coil3.size.Dimension
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import net.sigmabeta.chipbox.images.api.HatchetCoilLogger
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.components.GridImageSize
import net.sigmabeta.sage.logging.Hatchet

/**
 * Coil [ImageLoader] for the browser.
 *
 * - **Network fetcher** — Coil 3's default image loader on JS has no fetcher registered for
 *   http(s) URLs; [KtorNetworkFetcherFactory] (using the same Ktor engine `RemoteRepository`
 *   uses) wires one in. Without it every image request errors.
 *
 * - **baseUrl prepender + size hint** — the server's JSON serialization layer (see server
 *   `PublicUrls`) hands the browser opaque endpoint paths like `/api/games/42/cover`.
 *   [ServerUrlMapper] prepends [baseUrl] so the Ktor fetcher sees a fully-qualified URL, and
 *   appends a `?size=<bucket-pixels>` query parameter so the server can resize the cover to a
 *   thumbnail before sending. http(s) and `data:` URLs flow through unchanged (preview
 *   fixtures + external covers).
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
 * Prepends [baseUrl] to relative server paths and tacks a `?size=<bucket-pixels>` query param
 * on so the server can resize the image down to the requested thumbnail size. Matches the
 * bucketing `CrossfadeImage` already does for memory-cache keys — keeps client + server in
 * sync on which buckets are valid.
 *
 * http(s)/data URLs pass through unchanged so external image URLs and preview fixtures still
 * work.
 */
private class ServerUrlMapper(private val baseUrl: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? {
        if (data.startsWith("http://") || data.startsWith("https://")) return null
        if (data.startsWith("data:")) return null
        val size = bucketFor(options) ?: return "$baseUrl$data"
        val separator = if ('?' in data) '&' else '?'
        return "$baseUrl$data${separator}size=${size.pixels}"
    }

    /**
     * Pick the smallest [GridImageSize] whose `pixels` ≥ the max dimension Coil resolved this
     * request to; fall back to the largest bucket if nothing fits, or null if Coil couldn't
     * resolve a pixel size (e.g. ORIGINAL — let the server send the full-res image).
     */
    private fun bucketFor(options: Options): GridImageSize? {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 0
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 0
        val maxDim = maxOf(w, h)
        if (maxDim <= 0) return null
        return GridImageSize.entries.firstOrNull { maxDim <= it.pixels }
            ?: GridImageSize.entries.last()
    }
}
