package net.sigmabeta.chipbox.player.emulators.fake

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.fake.models.PitchClass
import net.sigmabeta.chipbox.player.emulators.fake.models.ScaleMode
import net.sigmabeta.chipbox.player.emulators.fake.models.TimeSignature
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [TrackRandomizer] is a seeded generator: every input trackId picks a single output. This suite
 * pins the determinism contract (same id → same track), the time-signature divisor priority that
 * picks the meter, and the documented tempo / scale ranges so a future "tweak" doesn't reshape
 * existing demo tracks without anyone noticing.
 */
class TrackRandomizerTest {

    private val hatchet = BluntHatchet()

    @Test
    fun `same track id produces identical generated tracks`() {
        // The whole point of seeding from trackId: re-rendering the same track must reproduce
        // every note, scale, tempo, and meter. If this breaks the fake emulator becomes
        // non-deterministic and listening tests start fighting each other.
        val track = fakeTrack(id = 42L, lengthMs = 5_000L)
        val a = TrackRandomizer.generate(track, hatchet)
        val b = TrackRandomizer.generate(track, hatchet)
        assertEquals(a, b)
    }

    @Test
    fun `different track ids generally produce different tracks`() {
        // Not strictly required by contract — two seeds could collide on every random call — but
        // for two nearby small ids they should diverge somewhere (meter, scale, or measures).
        val a = TrackRandomizer.generate(fakeTrack(id = 1L, lengthMs = 5_000L), hatchet)
        val b = TrackRandomizer.generate(fakeTrack(id = 2L, lengthMs = 5_000L), hatchet)
        assertNotEquals(a, b)
    }

    @Test
    fun `tempo lands in the documented 60-199 BPM band`() {
        // TEMPO_MIN_BPM=60, TEMPO_RANGE_BPM=140 → nextInt(140)+60 yields [60, 199].
        // Sample a handful of ids to make sure nothing slips out.
        for (id in 1L..32L) {
            val tempo = TrackRandomizer.generate(fakeTrack(id, lengthMs = 1_000L), hatchet).tempo
            assertTrue(tempo in 60..199, "trackId=$id produced out-of-band tempo $tempo")
        }
    }

    @Test
    fun `trackId divisible by 7 produces a FIVE meter`() {
        // DIVISOR_FIVE=7 is checked first, so any multiple of 7 picks FIVE — even when it's also
        // a multiple of one of the lower-priority divisors (e.g. 14 = 7*2).
        assertEquals(TimeSignature.FIVE, meterFor(7L))
        assertEquals(TimeSignature.FIVE, meterFor(14L)) // also div by 2 — 7 wins
        assertEquals(TimeSignature.FIVE, meterFor(77L)) // also div by 11 — 7 wins
        assertEquals(TimeSignature.FIVE, meterFor(91L)) // also div by 13 — 7 wins
    }

    @Test
    fun `trackId divisible by 11 but not 7 produces BLUE_RONDO`() {
        assertEquals(TimeSignature.BLUE_RONDO, meterFor(11L))
        assertEquals(TimeSignature.BLUE_RONDO, meterFor(22L)) // 11*2 — 11 takes precedence over 2
    }

    @Test
    fun `trackId divisible by 13 but not 7 or 11 produces UNSQUARE`() {
        assertEquals(TimeSignature.UNSQUARE, meterFor(13L))
        assertEquals(TimeSignature.UNSQUARE, meterFor(26L)) // 13*2 — 13 still wins
    }

    @Test
    fun `even trackIds not divisible by 7-11-13 produce COMMON`() {
        // Plain 4/4 is the fall-through for "even but not one of the prime hooks".
        assertEquals(TimeSignature.COMMON, meterFor(2L))
        assertEquals(TimeSignature.COMMON, meterFor(4L))
        assertEquals(TimeSignature.COMMON, meterFor(8L))
    }

    @Test
    fun `odd trackIds with no divisor hits pick MARCH or WALTZ at random`() {
        // The else branch is the only one that uses the random source, so output is one of the two.
        val meters = (1L..30L step 2L)
            .filter { it !in setOf(7L, 11L, 13L, 21L, 27L) } // skip multiples of the prime hooks
            .map { meterFor(it) }
            .toSet()
        // Across 11 odd numbers we should hit both at least once — and never anything else.
        assertTrue(meters.all { it == TimeSignature.MARCH || it == TimeSignature.WALTZ })
    }

    @Test
    fun `generated scale uses a real PitchClass and ScaleMode`() {
        // No enum-out-of-bounds shenanigans: random.nextValue(values()) must always land on a
        // defined enum entry.
        for (id in 1L..16L) {
            val scale = TrackRandomizer.generate(fakeTrack(id, lengthMs = 1_000L), hatchet).scale
            assertTrue(scale.root in PitchClass.values(), "trackId=$id produced bogus root ${scale.root}")
            assertTrue(scale.mode in ScaleMode.values(), "trackId=$id produced bogus mode ${scale.mode}")
        }
    }

    @Test
    fun `generated track contains at least one measure for any positive length`() {
        // Edge case: even a 1 ms length should produce SOMETHING (the loop adds a whole measure
        // before checking length), not an empty measures list that would crash playback.
        val gen = TrackRandomizer.generate(fakeTrack(id = 1L, lengthMs = 1L), hatchet)
        assertTrue(gen.measures.isNotEmpty(), "expected at least one measure, got empty list")
    }

    @Test
    fun `generated trackLengthMs mirrors the input track length`() {
        // Output's lengthMs comes from input.trackLengthMs.toDouble() — verify the conversion
        // doesn't drift (e.g. someone "fixing" it to a measure-rounded value).
        val gen = TrackRandomizer.generate(fakeTrack(id = 1L, lengthMs = 12_345L), hatchet)
        assertEquals(12_345.0, gen.trackLengthMs)
    }

    private fun meterFor(id: Long): TimeSignature =
        TrackRandomizer.generate(fakeTrack(id, lengthMs = 1_000L), hatchet).timeSignature

    private fun fakeTrack(id: Long, lengthMs: Long): Track = Track(
        id = id,
        path = "test.fake",
        source = "test",
        title = "Test $id",
        trackLengthMs = lengthMs,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )
}
