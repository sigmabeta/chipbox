package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.test.runTest
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [LruCache]: it loads on a miss, serves cached values without re-invoking the loader,
 * evicts the least-recently-used entry once capacity is exceeded (with hits counting as use),
 * empties on [LruCache.clear], and logs a line on each miss (but not on hits).
 */
class LruCacheTest {

    private fun cache(maxSize: Int, hatchet: Hatchet = BluntHatchet()) =
        LruCache<Long, String>(maxSize, name = "test", hatchet = hatchet)

    @Test
    fun `getOrLoad runs the loader on a miss and returns its value`() = runTest {
        val cache = cache(maxSize = 2)
        var loads = 0

        val value = cache.getOrLoad(1L) {
            loads++
            "one"
        }

        assertEquals("one", value)
        assertEquals(1, loads)
    }

    @Test
    fun `getOrLoad serves a cached value without re-invoking the loader`() = runTest {
        val cache = cache(maxSize = 2)
        var loads = 0

        cache.getOrLoad(1L) {
            loads++
            "one"
        }
        val second = cache.getOrLoad(1L) {
            loads++
            "should-not-run"
        }

        assertEquals("one", second)
        assertEquals(1, loads)
    }

    @Test
    fun `exceeding capacity evicts the least-recently-used entry`() = runTest {
        val cache = cache(maxSize = 2)
        val loads = mutableListOf<Long>()
        suspend fun load(key: Long, value: String) = cache.getOrLoad(key) {
            loads += key
            value
        }

        load(1L, "one")
        load(2L, "two")
        load(3L, "three") // evicts 1 (eldest)

        loads.clear()
        load(2L, "two-again") // still cached → no load
        load(3L, "three-again") // still cached → no load
        load(1L, "one-again") // was evicted → reloads

        assertEquals(listOf(1L), loads)
    }

    @Test
    fun `a cache hit refreshes recency so a different entry is evicted`() = runTest {
        val cache = cache(maxSize = 2)
        val loads = mutableListOf<Long>()
        suspend fun load(key: Long, value: String) = cache.getOrLoad(key) {
            loads += key
            value
        }

        load(1L, "one")
        load(2L, "two")
        load(1L, "one") // hit → 1 is now most-recently-used, 2 is eldest
        load(3L, "three") // evicts 2, not 1

        loads.clear()
        load(1L, "one-again") // still cached → no load
        load(2L, "two-again") // was evicted → reloads

        assertEquals(listOf(2L), loads)
    }

    @Test
    fun `clear empties the cache so the next access reloads`() = runTest {
        val cache = cache(maxSize = 2)
        var loads = 0

        cache.getOrLoad(1L) {
            loads++
            "one"
        }
        cache.clear()
        cache.getOrLoad(1L) {
            loads++
            "one"
        }

        assertEquals(2, loads)
    }

    @Test
    fun `only misses log, carrying the running hit-rate`() = runTest {
        val logger = RecordingHatchet()
        val cache = cache(maxSize = 2, hatchet = logger)

        cache.getOrLoad(1L) { "one" } // miss → logs
        cache.getOrLoad(1L) { "one" } // hit → silent
        cache.getOrLoad(2L) { "two" } // miss → logs, rate reflects the prior hit

        assertEquals(2, logger.verbose.size)
        assertTrue(logger.verbose[0].contains("miss key=1"), logger.verbose[0])
        assertTrue(logger.verbose[1].contains("miss key=2"), logger.verbose[1])
        assertTrue(logger.verbose[1].contains("1/3 (33% hit)"), logger.verbose[1])
    }

    private class RecordingHatchet : Hatchet {
        val verbose = mutableListOf<String>()
        override fun v(message: String) {
            verbose += message
        }
        override fun d(message: String) = Unit
        override fun i(message: String) = Unit
        override fun w(message: String) = Unit
        override fun e(message: String) = Unit
        override fun log(severity: Int, message: String) = Unit
    }
}
