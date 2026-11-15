package net.sigmabeta.chipbox.player.cache

/**
 * Stable identity for a single rendered PCM stream in the cache.
 *
 * The triple `(sourceHash, trackNumber, renderVersion)` is what makes a cache entry unique:
 * - [sourceHash] folds in the chip-file source bytes plus every chain file the emulator would
 *   resolve, so a substituted `_lib.psf` produces a fresh key.
 * - [trackNumber] separates sub-tracks of multi-track formats (NSF, GBS) that share a single
 *   source file but produce different audio per track index.
 * - [renderVersion] is the rendering backend's output version: bumping it when a change makes the
 *   same input render different PCM (a new output rate, an emulation fix) retires the old entries.
 *
 * The output sample rate is deliberately *not* part of the key. It lives in the cache file's
 * header, so a warm hit can be served without loading the track to learn the rate. That matters
 * for backends whose rate depends on the track (GSF settles on the rate its driver programs).
 *
 * The on-disk filename is derived from this key — see [filename].
 */
data class PcmCacheKey(
    val sourceHash: String,
    val trackNumber: Int,
    val renderVersion: Int,
) {
    fun filename(): String = "$sourceHash-$trackNumber-r$renderVersion.pcm"

    fun tempFilename(): String = "${filename()}.tmp"
}
