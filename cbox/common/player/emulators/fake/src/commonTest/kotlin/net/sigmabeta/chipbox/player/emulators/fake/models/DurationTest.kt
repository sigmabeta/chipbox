package net.sigmabeta.chipbox.player.emulators.fake.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurationTest {

    @Test
    fun `quarter note at 60 BPM is exactly one second`() {
        // The textbook calibration — 60 BPM means 60 quarter notes per minute means 1 s each.
        assertEquals(1_000.0, Duration.QUARTER.toMsAtTempo(60))
    }

    @Test
    fun `whole note is four times the quarter note`() {
        // beats * 60_000 / bpm — verify the ratios stay aligned across the whole enum.
        val tempo = 120
        val quarter = Duration.QUARTER.toMsAtTempo(tempo)
        val whole = Duration.WHOLE.toMsAtTempo(tempo)
        assertEquals(quarter * 4, whole, 1e-6)
    }

    @Test
    fun `dotted notes are 1_5x their plain counterpart`() {
        // A dotted-half is 1.5x a plain-half, per music notation. The enum values bake this in;
        // pin it so a refactor to "compute the dot at use-site" doesn't shift the constants.
        val tempo = 120
        assertEquals(Duration.HALF.toMsAtTempo(tempo) * 1.5, Duration.HALF_DOTTED.toMsAtTempo(tempo), 1e-6)
        assertEquals(Duration.QUARTER.toMsAtTempo(tempo) * 1.5, Duration.QUARTER_DOTTED.toMsAtTempo(tempo), 1e-6)
    }

    @Test
    fun `tempo doubling halves note duration`() {
        // Linear inverse: BPM up, ms down. Cross-check at two tempos.
        val slow = Duration.QUARTER.toMsAtTempo(60)
        val fast = Duration.QUARTER.toMsAtTempo(120)
        assertEquals(slow / 2.0, fast, 1e-6)
    }

    @Test
    fun `every duration value has a positive ms reading`() {
        // Cheap regression: no value should be zero or negative at any sensible tempo.
        for (d in Duration.values()) assertTrue(d.toMsAtTempo(60) > 0, "$d came back non-positive")
    }

    private fun assertEquals(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            kotlin.math.abs(expected - actual) < tolerance,
            "expected $expected, got $actual (delta ${kotlin.math.abs(expected - actual)})",
        )
    }
}
