package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.sage.logging.Hatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoudnessLogTest {

    @Test
    fun `non-finite loudness logs the no-measurement line and returns`() {
        // NaN happens on the first 400 ms of a render — no integrated value yet. Verify the log
        // says so and *doesn't* attempt to compute a gain (which would also be NaN).
        val hatchet = CapturingHatchet()
        LoudnessLog.report(hatchet, "Track", loudnessLufs = Double.NaN, truePeakDbtp = -3.0)
        assertEquals(listOf("i: Track Track: no usable loudness measurement."), hatchet.messages)
    }

    @Test
    fun `positive LUFS reading is treated as corrupt and skipped`() {
        // LUFS is always <= 0 in a sane measurement — a positive value points at a bad header
        // field. Don't try to use it.
        val hatchet = CapturingHatchet()
        LoudnessLog.report(hatchet, "Track", loudnessLufs = 4.0, truePeakDbtp = -3.0)
        assertEquals(listOf("i: Track Track: no usable loudness measurement."), hatchet.messages)
    }

    @Test
    fun `valid loudness and true peak include both measurements plus the gain multiplier`() {
        // The full report: LUFS to one decimal, dBTP to one decimal, gain to three decimals,
        // target -14 LUFS. Exact format pinned so log scrapers (and the user reading them
        // alongside playback) stay aligned.
        val hatchet = CapturingHatchet()
        LoudnessLog.report(hatchet, "Track", loudnessLufs = -24.0, truePeakDbtp = -10.0)
        val message = hatchet.messages.single()
        assertTrue(message.startsWith("i: Track Track: "), "wrong prefix: $message")
        assertTrue(message.contains("-24.0 LUFS"), "missing LUFS: $message")
        assertTrue(message.contains("-10.0 dBTP"), "missing dBTP: $message")
        assertTrue(message.contains("to reach -14 LUFS"), "missing target: $message")
        // Gain ≈ min(10^((-14 - -24)/20), 10^((-1 - -10)/20)) = min(3.162, 2.818) ≈ 2.818 — peak-capped.
        assertTrue(message.contains("2.818x"), "expected peak-capped multiplier; got: $message")
    }

    @Test
    fun `non-finite true peak renders as an em-dash placeholder`() {
        // No measured peak ⇒ no ceiling; the formatter shows "—" rather than "NaN dBTP" so
        // the log is readable instead of leaking internals.
        val hatchet = CapturingHatchet()
        LoudnessLog.report(hatchet, "Track", loudnessLufs = -24.0, truePeakDbtp = Double.NaN)
        val message = hatchet.messages.single()
        assertTrue(message.contains("—"), "expected em-dash placeholder; got: $message")
    }

    /** Hatchet impl that buffers every message so tests can assert against them.
     *  Each entry is "<level>: <message>" so a one-line assert can cover both fields. */
    private class CapturingHatchet : Hatchet {
        val messages = mutableListOf<String>()
        override fun v(message: String) {
            messages += "v: $message"
        }
        override fun d(message: String) {
            messages += "d: $message"
        }
        override fun i(message: String) {
            messages += "i: $message"
        }
        override fun w(message: String) {
            messages += "w: $message"
        }
        override fun e(message: String) {
            messages += "e: $message"
        }
        override fun log(severity: Int, message: String) {
            messages += "log[$severity]: $message"
        }
    }
}
