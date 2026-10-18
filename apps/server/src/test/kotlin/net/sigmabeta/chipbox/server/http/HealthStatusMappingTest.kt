package net.sigmabeta.chipbox.server.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.models.ScanStatus
import net.sigmabeta.chipbox.scanner.state.ScannerState

/**
 * `/api/health` must expose the real scanner state instead of a hard-coded `ok`, and must never
 * echo the host's absolute library path. The mapping is pure so it can be tested without a server.
 */
class HealthStatusMappingTest {

    @Test
    fun `unknown and idle are healthy and not scanning`() {
        val unknown = ScannerState.Unknown.toHealthStatus()
        assertTrue(unknown.ok)
        assertEquals(ScanStatus.UNKNOWN, unknown.state.status)
        assertFalse(unknown.scanning)

        val idle = ScannerState.Idle.toHealthStatus()
        assertTrue(idle.ok)
        assertEquals(ScanStatus.IDLE, idle.state.status)
        assertFalse(idle.scanning)
    }

    @Test
    fun `scanning exposes live counters and elapsed time`() {
        val health = ScannerState.Scanning(
            timeInSeconds = 3,
            gamesFound = 2,
            tracksFound = 10,
            tracksFailed = 1,
        ).toHealthStatus()

        assertTrue(health.ok)
        assertTrue(health.scanning)
        assertEquals(ScanStatus.SCANNING, health.state.status)
        assertEquals(3, health.state.timeInSeconds)
        assertEquals(2, health.games)
        assertEquals(10, health.tracks)
        assertEquals(1, health.tracksFailed)
        assertNull(health.error)
    }

    @Test
    fun `complete keeps the final counters and is no longer scanning`() {
        val health = ScannerState.Complete(
            timeInSeconds = 12,
            gamesFound = 4,
            tracksFound = 40,
            tracksFailed = 2,
        ).toHealthStatus()

        assertTrue(health.ok)
        assertFalse(health.scanning)
        assertEquals(ScanStatus.COMPLETE, health.state.status)
        assertEquals(12, health.state.timeInSeconds)
        assertEquals(4, health.games)
        assertEquals(40, health.tracks)
        assertEquals(2, health.tracksFailed)
    }

    @Test
    fun `failed scan is not ok and exposes only the failed file basename`() {
        val health = ScannerState.Failed("/music/Some Game/broken.spc").toHealthStatus()

        assertFalse(health.ok)
        assertFalse(health.scanning)
        assertEquals(ScanStatus.FAILED, health.state.status)
        assertEquals("broken.spc", health.state.failedFile)
        assertEquals("broken.spc", health.error)
    }

    @Test
    fun `serialized health never contains a host library path`() {
        val json = Json.encodeToString(ScannerState.Failed("/music/Some Game/broken.spc").toHealthStatus())

        assertFalse("/music" in json)
        assertFalse("Some Game" in json)
    }
}
