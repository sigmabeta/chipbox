package net.sigmabeta.chipbox.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The read loop the browser uses for its initial library load. It is extracted from the Ktor layer
 * so the retry/timeout behaviour is testable without a browser or a running server.
 */
class ScanReadinessTest {

    @Test
    fun `returns a non-empty read immediately without consulting health`() = runTest {
        var healthReads = 0

        val result = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = listOf(listOf("track")),
            health = {
                healthReads++
                healthFor(ScanStatus.SCANNING)
            },
        )

        assertEquals(listOf("track"), result)
        assertEquals(0, healthReads)
    }

    @Test
    fun `retries empty reads while scanning and returns the library once the scan finishes`() = runTest {
        val result = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = listOf(emptyList(), emptyList(), listOf("track-a", "track-b")),
            health = { healthFor(ScanStatus.SCANNING) },
        )

        assertEquals(listOf("track-a", "track-b"), result)
    }

    @Test
    fun `returns empty once the scan is no longer running`() = runTest {
        val result = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = listOf(emptyList()),
            health = { healthFor(ScanStatus.COMPLETE) },
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun `stops at the attempt cap instead of polling forever`() = runTest {
        var reads = 0
        var delays = 0

        val result = readListAwaitingScan<String>(
            policy = ScanRetryPolicy(maxAttempts = 3, delayMillis = 10),
            read = {
                reads++
                emptyList()
            },
            readHealth = { healthFor(ScanStatus.SCANNING) },
            delay = { delays++ },
        )

        assertEquals(emptyList(), result)
        assertEquals(4, reads) // first read + 3 retries
        assertEquals(3, delays)
    }

    @Test
    fun `treats an unreachable health endpoint as a finished scan`() = runTest {
        val result = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = listOf(emptyList()),
            health = { null },
        )

        assertEquals(emptyList(), result)
    }

    private suspend fun runReadinessTest(
        policy: ScanRetryPolicy,
        reads: List<List<String>>,
        health: suspend () -> HealthStatus?,
    ): List<String> {
        var index = 0
        return readListAwaitingScan(
            policy = policy,
            read = { reads[minOf(index++, reads.lastIndex)] },
            readHealth = health,
            delay = { assertTrue(it >= 0) },
        )
    }

    private fun healthFor(status: ScanStatus) =
        HealthStatus(ok = status != ScanStatus.FAILED, state = ScanState(status))
}
