package net.sigmabeta.chipbox.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * The read flow the browser uses for its initial library load. It is extracted from the Ktor layer
 * so the retry/timeout behaviour is testable without a browser or a running server.
 */
class ScanReadinessTest {

    @Test
    fun `a non-empty read is emitted immediately and not re-read once the scan has ended`() = runTest {
        var reads = 0

        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { _ ->
                reads++
                listOf("track")
            },
            health = { healthFor(ScanStatus.COMPLETE) },
        )

        assertEquals(listOf(listOf("track")), emissions)
        assertEquals(1, reads, "a finished scan means no second read")
    }

    @Test
    fun `a partial non-empty read is painted then refreshed when the scan completes`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { attempt ->
                if (attempt == 0) listOf("track-a") else listOf("track-a", "track-b")
            },
            health = { attempt ->
                healthFor(if (attempt == 0) ScanStatus.SCANNING else ScanStatus.COMPLETE)
            },
        )

        assertEquals(listOf(listOf("track-a"), listOf("track-a", "track-b")), emissions)
    }

    @Test
    fun `retries empty reads while scanning and returns the library once the scan finishes`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { attempt ->
                if (attempt < 2) emptyList() else listOf("track-a", "track-b")
            },
            health = { healthFor(ScanStatus.SCANNING) },
        )

        assertEquals(listOf(listOf("track-a", "track-b")), emissions, "no empty flash while the scan runs")
    }

    @Test
    fun `an initial unknown health state keeps retrying until the scan starts`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { attempt -> if (attempt == 0) emptyList() else listOf("track") },
            health = { attempt ->
                healthFor(if (attempt == 0) ScanStatus.UNKNOWN else ScanStatus.SCANNING)
            },
        )

        assertEquals(listOf(listOf("track")), emissions)
    }

    @Test
    fun `returns empty once the scan is no longer running`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { _ -> emptyList() },
            health = { healthFor(ScanStatus.COMPLETE) },
        )

        assertEquals(listOf(emptyList()), emissions)
    }

    @Test
    fun `stops at the attempt cap instead of polling forever`() = runTest {
        var reads = 0
        var delays = 0

        val emissions = readListAwaitingScan<String>(
            policy = ScanRetryPolicy(maxAttempts = 3, delayMillis = 10),
            read = {
                reads++
                emptyList()
            },
            readHealth = { healthFor(ScanStatus.SCANNING) },
            delay = { delays++ },
        ).toList()

        assertEquals(listOf(emptyList()), emissions)
        assertEquals(4, reads) // first read + 3 retries
        assertEquals(3, delays)
    }

    @Test
    fun `an attempt-capped partial read keeps the partial library`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 1, delayMillis = 10),
            reads = { _ -> listOf("partial") },
            health = { healthFor(ScanStatus.SCANNING) },
        )

        assertEquals(listOf(listOf("partial")), emissions)
    }

    @Test
    fun `treats an unreachable health endpoint as a finished scan`() = runTest {
        val emissions = runReadinessTest(
            policy = ScanRetryPolicy(maxAttempts = 5, delayMillis = 10),
            reads = { _ -> emptyList() },
            health = { null },
        )

        assertEquals(listOf(emptyList()), emissions)
    }

    private suspend fun runReadinessTest(
        policy: ScanRetryPolicy,
        reads: suspend (attempt: Int) -> List<String>,
        health: suspend (attempt: Int) -> HealthStatus?,
    ): List<List<String>> {
        var attempt = 0
        return readListAwaitingScan(
            policy = policy,
            read = { reads(attempt) },
            readHealth = { health(attempt).also { attempt++ } },
            delay = { assertTrue(it >= 0) },
        ).toList()
    }

    private fun healthFor(status: ScanStatus) =
        HealthStatus(ok = status != ScanStatus.FAILED, state = ScanState(status))
}
