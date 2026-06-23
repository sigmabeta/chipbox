package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sigmabeta.sage.logging.Hatchet

/**
 * A tiny coroutine-safe least-recently-used cache.
 *
 * commonMain has no access-order [LinkedHashMap] constructor (that overload is JVM-only), so
 * recency is maintained by hand: every hit and insert re-puts the entry at the tail, and the
 * head (eldest) is dropped once [maxSize] is exceeded.
 *
 * [DatabaseRepository] uses one instance per hydration resolver so that each cached value has a
 * fixed shape and the key is just the parent row id.
 *
 * Misses log a line with the running hit-rate at verbose level (tagged with [name]) — enough to
 * confirm the cache earns its keep without a log line on every hit, and filtered out by default in
 * release builds. Counters reset on [clear].
 */
internal class LruCache<K, V>(
    private val maxSize: Int,
    private val name: String,
    private val hatchet: Hatchet,
) {
    private val map = LinkedHashMap<K, V>()
    private val mutex = Mutex()

    private var hits = 0
    private var misses = 0

    /**
     * Returns the cached value for [key], or runs [loader] and caches the result.
     *
     * [loader] runs OUTSIDE the lock so concurrent hydration of different keys isn't serialized on
     * a single DB roundtrip. Two coroutines racing on the same missing key may both load — that's
     * benign (they read the same row) and the later put simply wins.
     */
    suspend fun getOrLoad(key: K, loader: suspend () -> V): V {
        mutex.withLock {
            val cached = map.remove(key)
            if (cached != null) {
                map[key] = cached
                hits++
                return cached
            }
        }

        val value = loader()

        mutex.withLock {
            map.remove(key)
            map[key] = value
            if (map.size > maxSize) {
                map.remove(map.keys.iterator().next())
            }
            misses++
            logMiss(key)
        }
        return value
    }

    suspend fun clear() = mutex.withLock {
        map.clear()
        hits = 0
        misses = 0
    }

    // Called while holding [mutex], so the counters it reads are consistent.
    private fun logMiss(key: K) {
        val total = hits + misses
        val rate = if (total == 0) 0 else hits * 100 / total
        hatchet.v("LruCache[$name] miss key=$key — $hits/$total ($rate% hit)")
    }
}
