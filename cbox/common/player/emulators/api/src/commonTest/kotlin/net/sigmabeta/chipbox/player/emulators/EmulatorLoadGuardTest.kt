package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the native-emulator use-after-free that crashed the GBA core (SIGSEGV in
 * `GBACoreRunFrame`, NULL `m_core`) — and surfaced as a "No track loaded" error on GME.
 *
 * The emulators are process-wide singletons over global C state, so a track handoff can run
 * [Emulator.teardown] (which frees that state) on one coroutine while the outgoing track's
 * render-ahead writer is still calling [Emulator.generateBuffer] on another. The fix guards
 * `generateBuffer` so it never crosses into native unless a track is currently loaded, and only
 * marks a track loaded when [Emulator.loadTrackInternal] reports no error.
 */
class EmulatorLoadGuardTest {

    @Test
    fun `generateBuffer runs the native core after a successful load`() {
        val emulator = RecordingEmulator()
        emulator.loadTrack(trackOf())

        val produced = emulator.generateBuffer(ShortArray(BUFFER_SHORTS))

        assertEquals(1, emulator.generateCalls, "a loaded core should fill from native")
        assertTrue(produced > 0, "a loaded core should produce frames")
    }

    @Test
    fun `generateBuffer skips the native core after teardown`() {
        val emulator = RecordingEmulator()
        emulator.loadTrack(trackOf())
        emulator.teardown()

        val produced = emulator.generateBuffer(ShortArray(BUFFER_SHORTS))

        assertEquals(
            0,
            emulator.generateCalls,
            "generateBuffer must not call into native after teardown — that's the use-after-free",
        )
        assertEquals(0, produced, "a torn-down core produces no frames")
    }

    @Test
    fun `a failed load leaves the core unloaded so generateBuffer skips native`() {
        val emulator = RecordingEmulator(loadError = "Invalid GSF")
        emulator.loadTrack(trackOf())

        val produced = emulator.generateBuffer(ShortArray(BUFFER_SHORTS))

        assertEquals(
            0,
            emulator.generateCalls,
            "a load that reported an error must not be generated from",
        )
        assertEquals(0, produced, "a failed load produces no frames")
    }

    /** Concrete [Emulator] that counts native generate calls and can simulate a load error. */
    private class RecordingEmulator(private val loadError: String? = null) : Emulator() {
        var generateCalls = 0
            private set

        override val supportedFileExtensions = listOf("gsf")
        override fun loadNativeLib() = Unit
        override fun loadTrackInternal(path: String) = Unit
        override fun teardownInternal() = Unit
        override fun getLastError(): String? = loadError
        override fun getSampleRateInternal() = SAMPLE_RATE

        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int {
            generateCalls++
            return framesPerBuffer
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BUFFER_SHORTS = 8

        fun trackOf(): Track = Track(
            id = 1L,
            path = "/library/track.gsf",
            source = "test",
            title = "Track",
            trackLengthMs = 60_000L,
            trackNumber = 0,
            fadeLengthMs = 0L,
            game = null,
            artists = null,
            platform = Platform.GAMEBOY,
        )
    }
}
