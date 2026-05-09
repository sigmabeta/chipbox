package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track

/**
 * Computes the cache key's source hash from a track's source bytes plus every chain file the
 * native emulator would resolve.
 *
 * Folding chain files into the hash is the difference between safe and unsafe caching: PSF/2SF
 * tracks resolve auxiliary `_lib.*` files at load time, and an updated lib file produces
 * different audio from byte-identical main bytes. Hashing only the main bytes would silently
 * return stale audio after a chain-file edit.
 *
 * FNV-1a 64-bit is used rather than SHA-1 / SHA-256 because hashing happens on the playback
 * hot path: a 100MB PSF would cost 200–400ms with SHA-256 but ~50ms with FNV-1a, which we can
 * afford at track-load time. Collision risk is acceptable for a content-addressed cache —
 * worst case is one wrong play before the user notices and re-imports.
 */
internal class PcmCacheHasher(
    private val contentSourceRegistry: ContentSourceRegistry,
) {
    suspend fun hash(track: Track, mainBytes: ByteArray): String {
        var hash = FNV_OFFSET_BASIS
        hash = fold(hash, mainBytes)

        if (track.chainFiles.isNotEmpty()) {
            val source = contentSourceRegistry.get(track.source)
            val sortedChain = track.chainFiles.sortedBy { it.filename }
            for (chain in sortedChain) {
                hash = fold(hash, chain.filename.encodeToByteArray())
                val chainBytes = source?.openBytes(chain.uri)
                if (chainBytes != null) {
                    hash = fold(hash, chainBytes)
                }
            }
        }

        return hash.toULong().toString(16).padStart(16, '0')
    }

    private fun fold(seed: Long, bytes: ByteArray): Long {
        var hash = seed
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFFL)
            hash *= FNV_PRIME
        }
        return hash
    }

    companion object {
        // 64-bit FNV-1a constants (https://en.wikipedia.org/wiki/Fowler–Noll–Vo_hash_function).
        private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xCBF29CE484222325
        private const val FNV_PRIME = 1099511628211L
    }
}
