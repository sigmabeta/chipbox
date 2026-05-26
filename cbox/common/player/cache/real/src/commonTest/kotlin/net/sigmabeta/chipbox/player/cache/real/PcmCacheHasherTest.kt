package net.sigmabeta.chipbox.player.cache.real

import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [PcmCacheHasher] is the keystone of safe caching: a stable, fast hash of a track's source bytes
 * plus its resolved chain files. The cache returns stale audio if this changes silently, so pin
 * the algorithm choice (FNV-1a 64-bit, the documented well-known function) with a canonical
 * vector and lock the chain-file folding rules.
 */
class PcmCacheHasherTest {

    private val registry = ContentSourceRegistry(setOf(FakeSource("test", mapOf(
        "lib-a" to byteArrayOf(0x01, 0x02),
        "lib-b" to byteArrayOf(0x03, 0x04),
    ))))
    private val hasher = PcmCacheHasher(registry)

    @Test
    fun `empty input produces the canonical FNV-1a 64-bit offset basis`() = runTest {
        // FNV-1a starts at 0xCBF29CE484222325 and folds nothing for empty input — match the
        // reference value so the algorithm itself can't be silently swapped to a different hash.
        val hash = hasher.hash(track(chainFiles = emptyList()), mainBytes = ByteArray(0))
        assertEquals("cbf29ce484222325", hash)
    }

    @Test
    fun `same bytes produce the same hash`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val a = hasher.hash(track(), bytes)
        val b = hasher.hash(track(), bytes)
        assertEquals(a, b)
    }

    @Test
    fun `different bytes produce different hashes`() = runTest {
        // Hash collisions exist in theory; for two trivially-different small inputs they don't.
        val a = hasher.hash(track(), byteArrayOf(1, 2, 3))
        val b = hasher.hash(track(), byteArrayOf(1, 2, 4))
        assertNotEquals(a, b)
    }

    @Test
    fun `output is exactly 16 hex chars zero-padded`() = runTest {
        // Hash width pinned so the on-disk PCM filename layout doesn't shift.
        repeat(8) { seed ->
            val hash = hasher.hash(track(), byteArrayOf(seed.toByte()))
            assertEquals(16, hash.length, "seed=$seed produced wrong length: $hash")
            assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "seed=$seed produced non-hex: $hash")
        }
    }

    @Test
    fun `chain files change the hash even when main bytes are identical`() = runTest {
        // The whole reason for hashing chain files: PSF/2SF resolve _lib siblings, and an updated
        // lib produces different audio from byte-identical mains. Without folding, the cache
        // would silently return stale audio after a lib edit.
        val main = byteArrayOf(1, 2, 3)
        val noChain = hasher.hash(track(chainFiles = emptyList()), main)
        val withChain = hasher.hash(
            track(chainFiles = listOf(ChainFile("lib-a", "lib-a"))),
            main,
        )
        assertNotEquals(noChain, withChain)
    }

    @Test
    fun `chain files are folded in filename-sorted order regardless of input order`() = runTest {
        // The hasher sorts chain files before folding — so two equivalent tracks listed in
        // different orders must hash the same. Without the sort, a scan-time reordering would
        // bust the cache.
        val main = byteArrayOf(1, 2, 3)
        val orderedAB = hasher.hash(
            track(chainFiles = listOf(ChainFile("lib-a", "lib-a"), ChainFile("lib-b", "lib-b"))),
            main,
        )
        val orderedBA = hasher.hash(
            track(chainFiles = listOf(ChainFile("lib-b", "lib-b"), ChainFile("lib-a", "lib-a"))),
            main,
        )
        assertEquals(orderedAB, orderedBA)
    }

    @Test
    fun `missing content source still produces a hash from filenames alone`() = runTest {
        // The chain bytes can't be fetched, but the chain filename still folds in — verify the
        // result is stable rather than crashing the call site (which would break cache lookups
        // for the track entirely).
        val hasherWithNoSource = PcmCacheHasher(ContentSourceRegistry(emptySet()))
        val hash = hasherWithNoSource.hash(
            track(chainFiles = listOf(ChainFile("missing-lib", "missing-lib"))),
            byteArrayOf(1, 2, 3),
        )
        assertEquals(16, hash.length)
    }

    private fun track(chainFiles: List<ChainFile> = emptyList()): Track = Track(
        id = 1L,
        path = "/library/track.psf",
        source = "test",
        title = "Track",
        trackLengthMs = 0L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        chainFiles = chainFiles,
        extension = "psf",
        platform = Platform.PSX,
    )

    /** Minimal in-memory [ContentSource] that returns fixed bytes per identifier — no I/O. */
    private class FakeSource(
        override val sourceId: String,
        private val bytesByPath: Map<String, ByteArray>,
    ) : ContentSource {
        override suspend fun openBytes(identifier: String): ByteArray? = bytesByPath[identifier]
    }
}
