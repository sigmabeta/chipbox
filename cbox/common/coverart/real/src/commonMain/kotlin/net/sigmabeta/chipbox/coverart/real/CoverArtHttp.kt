package net.sigmabeta.chipbox.coverart.real

import okio.IOException

/**
 * The cover-art subsystem's only platform boundary: the handful of HTTP calls it makes (Twitch
 * token, IGDB query, image download). Kept as an interface so the cover-art logic stays in
 * commonMain — the same reason the scanner takes a `VgmstreamProber` rather than a native lib. The
 * OkHttp-backed implementation lives in this module's jvmSharedMain and is injected by the app.
 *
 * All methods are blocking and throw [okio.IOException] on a non-success response or transport
 * failure; [IgdbClient] handles throttling/retries around them.
 */
interface CoverArtHttp {
    /** POST [body] (with [contentType] and [headers]) to [url]; returns the response body text. */
    fun post(url: String, headers: Map<String, String>, contentType: String, body: String): String

    /** POST the form-encoded [fields] to [url]; returns the response body text. */
    fun postForm(url: String, fields: Map<String, String>): String

    /** GET [url]; returns the raw response bytes (used to download a cover image). */
    fun getBytes(url: String): ByteArray
}

/** Convenience for impls: signals a failed cover-art HTTP call to the cover-art logic. */
fun coverArtHttpError(message: String): Nothing = throw IOException(message)
