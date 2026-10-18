package net.sigmabeta.chipbox.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The `/api/health` wire contract shared by apps/server (producer) and apps/js (consumer). These
 * tests pin the JSON shape the browser polls, and the bounded retry policy that decides when an
 * empty library read should be repeated while the server's startup scan is still running.
 */
class HealthStatusTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a scanning health payload with live counts`() {
        val payload = """
            {
              "ok": true,
              "state": { "status": "scanning", "timeInSeconds": 3 },
              "games": 2,
              "tracks": 10,
              "tracksFailed": 1
            }
        """.trimIndent()

        val health = json.decodeFromString<HealthStatus>(payload)

        assertTrue(health.ok)
        assertEquals(ScanStatus.SCANNING, health.state.status)
        assertEquals(3, health.state.timeInSeconds)
        assertTrue(health.scanning)
        assertEquals(2, health.games)
        assertEquals(10, health.tracks)
        assertEquals(1, health.tracksFailed)
        assertNull(health.error)
    }

    @Test
    fun `decodes a failed scan as not ok and names only the failed file`() {
        val payload = """
            {
              "ok": false,
              "state": { "status": "failed", "failedFile": "broken.spc" },
              "error": "broken.spc"
            }
        """.trimIndent()

        val health = json.decodeFromString<HealthStatus>(payload)

        assertFalse(health.ok)
        assertEquals(ScanStatus.FAILED, health.state.status)
        assertEquals("broken.spc", health.state.failedFile)
        assertEquals("broken.spc", health.error)
        assertFalse(health.scanning)
    }

    @Test
    fun `retries an empty library read while the server is scanning`() {
        val policy = ScanRetryPolicy(maxAttempts = 3, delayMillis = 0)
        val scanning = healthFor(ScanStatus.SCANNING)

        assertTrue(policy.shouldRetry(scanning, attempt = 0))
        assertTrue(policy.shouldRetry(scanning, attempt = 2))
    }

    @Test
    fun `stops retrying at the attempt cap so a slow scan cannot spin forever`() {
        val policy = ScanRetryPolicy(maxAttempts = 3, delayMillis = 0)

        assertFalse(policy.shouldRetry(healthFor(ScanStatus.SCANNING), attempt = 3))
        assertFalse(policy.shouldRetry(healthFor(ScanStatus.SCANNING), attempt = 99))
    }

    @Test
    fun `does not retry once the scan has finished or when health is unavailable`() {
        val policy = ScanRetryPolicy(maxAttempts = 3, delayMillis = 0)

        assertFalse(policy.shouldRetry(healthFor(ScanStatus.COMPLETE), attempt = 0))
        assertFalse(policy.shouldRetry(healthFor(ScanStatus.IDLE), attempt = 0))
        assertFalse(policy.shouldRetry(healthFor(ScanStatus.UNKNOWN), attempt = 0))
        assertFalse(policy.shouldRetry(healthFor(ScanStatus.FAILED), attempt = 0))
        assertFalse(policy.shouldRetry(health = null, attempt = 0))
    }

    private fun healthFor(status: ScanStatus) =
        HealthStatus(ok = status != ScanStatus.FAILED, state = ScanState(status))
}
