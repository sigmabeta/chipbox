package net.sigmabeta.chipbox.player.cache

/**
 * Stable identity for a single rendered PCM stream in the cache.
 *
 * The triple `(sourceHash, trackNumber, sampleRate)` is what makes a cache entry unique:
 * - [sourceHash] folds in the chip-file source bytes plus every chain file the emulator would
 *   resolve, so a substituted `_lib.psf` produces a fresh key.
 * - [trackNumber] separates sub-tracks of multi-track formats (NSF, GBS) that share a single
 *   source file but produce different audio per track index.
 * - [sampleRate] is part of the key because emulator output rate is not user-controllable; if
 *   we ever change the rate (e.g. an emulator update) the old cache becomes stale.
 *
 * The on-disk filename is derived from this key — see [filename].
 */
data class PcmCacheKey(
    val sourceHash: String,
    val trackNumber: Int,
    val sampleRate: Int,
) {
    fun filename(): String = "$sourceHash-$trackNumber-$sampleRate.pcm"

    fun tempFilename(): String = "${filename()}.tmp"
}
