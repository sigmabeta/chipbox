package net.sigmabeta.chipbox.models

/**
 * Reads a library list, retrying while the server's startup scan is still running and the read is
 * empty. The browser calls this for its first library loads: the server serves immediately but its
 * DB is empty until the background scan finishes, so a client opened too early would otherwise
 * render an empty Home forever.
 *
 * Non-empty reads return immediately without touching `/api/health`. Empty reads consult health and
 * retry only while [ScanRetryPolicy.shouldRetry] says so — a finished scan, an unreachable health
 * endpoint, or the attempt cap all end the wait with the empty result.
 */
suspend fun <T> readListAwaitingScan(
    policy: ScanRetryPolicy,
    read: suspend () -> List<T>,
    readHealth: suspend () -> HealthStatus?,
    delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
): List<T> {
    var attempt = 0
    while (true) {
        val items = read()
        if (items.isNotEmpty()) return items
        if (!policy.shouldRetry(readHealth(), attempt)) return items
        attempt++
        delay(policy.delayMillis)
    }
}
