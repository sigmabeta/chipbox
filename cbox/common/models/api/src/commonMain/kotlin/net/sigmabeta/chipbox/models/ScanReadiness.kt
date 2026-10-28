package net.sigmabeta.chipbox.models

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reads a library list, re-reading it as the server's startup scan progresses. The browser calls
 * this for its first library loads: the server serves immediately but its DB is incomplete until
 * the background scan finishes, so a client opened too early would otherwise render a stale or
 * empty library forever.
 *
 * Emissions:
 * - A **non-empty** read is emitted immediately (the user sees the partial library right away),
 *   and the list is read again once health reports the scan is no longer running, so the final
 *   contents replace the partial ones.
 * - An **empty** read while the scan is running is held back until either a non-empty read appears
 *   or the scan finishes (then the empty result is emitted).
 * - `unknown` health means the server has not started scanning yet, so it is treated as "still in
 *   progress" and retried. `idle`/`complete`/`failed`, an unreachable health endpoint, or the
 *   attempt cap all end the wait.
 *
 * Search reads do not call this (an empty search result is a legitimate answer, not a sign the
 * scan has not finished), and the flow is cold: a caller that only wants the current value can
 * take the first emission and cancel the rest.
 */
fun <T> readListAwaitingScan(
    policy: ScanRetryPolicy,
    read: suspend () -> List<T>,
    readHealth: suspend () -> HealthStatus?,
    delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
): Flow<List<T>> = flow {
    var attempt = 0
    var lastEmitted: List<T>? = null
    while (true) {
        val items = read()
        // Paint partial data as soon as it exists instead of blocking the first load on the scan.
        if (items.isNotEmpty() && items != lastEmitted) {
            emit(items)
            lastEmitted = items
        }
        if (!policy.shouldRetry(readHealth(), attempt)) {
            // Converge on the post-scan contents (including "now empty"), without repeating the
            // partial value the collector already saw.
            if (items != lastEmitted) emit(items)
            return@flow
        }
        attempt++
        delay(policy.delayMillis)
    }
}
