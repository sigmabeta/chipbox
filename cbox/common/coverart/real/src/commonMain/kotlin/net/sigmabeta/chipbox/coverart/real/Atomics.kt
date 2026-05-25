package net.sigmabeta.chipbox.coverart.real

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Compare-and-set retry loop for an atomic read-modify-write — the cover-art cache and overrides
 * keep their entries in an [AtomicReference] to a persistent map (immutable + CAS) instead of a
 * `synchronized` block, which isn't available in commonMain. Mirrors the helper in PcmCacheJanitor.
 */
@OptIn(ExperimentalAtomicApi::class)
internal inline fun <T> AtomicReference<T>.update(transform: (T) -> T) {
    while (true) {
        val current = load()
        if (compareAndSet(current, transform(current))) return
    }
}
