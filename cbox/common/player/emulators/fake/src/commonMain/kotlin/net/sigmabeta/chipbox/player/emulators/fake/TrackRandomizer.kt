package net.sigmabeta.chipbox.player.emulators.fake

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.isDivisibleBy
import net.sigmabeta.chipbox.player.emulators.fake.models.Duration
import net.sigmabeta.chipbox.player.emulators.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.emulators.fake.models.Measure
import net.sigmabeta.chipbox.player.emulators.fake.models.Note
import net.sigmabeta.chipbox.player.emulators.fake.models.PitchClass
import net.sigmabeta.chipbox.player.emulators.fake.models.Scale
import net.sigmabeta.chipbox.player.emulators.fake.models.ScaleMode
import net.sigmabeta.chipbox.player.emulators.fake.models.TimeSignature
import net.sigmabeta.sage.logging.Hatchet
import kotlin.math.floor
import kotlin.random.Random

object TrackRandomizer {

    fun generate(track: Track, hatchet: Hatchet): GeneratedTrack {
        val trackId = track.id

        val lengthMs = track.trackLengthMs.toDouble()
        var msGenerated = 0.0
        var measuresGenerated = 0
        var loops = 0

        val random = Random(trackId)

        val tempo = random.nextInt(TEMPO_RANGE_BPM) + TEMPO_MIN_BPM
        val timeSignature = generateTimeSignature(trackId, random)

        val generatedMeasures = mutableListOf<Measure>()

        val root = random.nextValue(PitchClass.values())
        val mode = random.nextValue(ScaleMode.values())
        val scale = Scale(root, mode)

        val measuresInLoop = random.nextValue(
            arrayOf(LOOP_LENGTH_SHORT, LOOP_LENGTH_MEDIUM, LOOP_LENGTH_LONG)
        )
        while (measuresGenerated < measuresInLoop) {
            val measure = generateMeasure(random, timeSignature, scale, hatchet)
            generatedMeasures.add(measure)
            hatchet.d("Generated $measure")
            measuresGenerated++
        }

        val trackMeasures = mutableListOf<Measure>()
        while (true) {
            for (measure in generatedMeasures) {
                trackMeasures.add(measure)
                val measureDuration = measure
                    .notes
                    .map { it.duration.toMsAtTempo(tempo) }
                    .sum()
                msGenerated += measureDuration

                hatchet.d("Adding measure with length $measureDuration")
                hatchet.d("Total song generated: $msGenerated / $lengthMs")
                if (msGenerated >= lengthMs) {
                    break
                }
            }

            hatchet.d("Looping for the $loops time")
            loops++

            if (msGenerated >= lengthMs) break
        }

        return GeneratedTrack(
            trackId,
            lengthMs,
            scale,
            timeSignature,
            tempo,
            trackMeasures
        )
    }

    private fun generateMeasure(
        random: Random,
        timeSignature: TimeSignature,
        scale: Scale,
        hatchet: Hatchet
    ): Measure {
        var beatsGenerated = 0.0
        val notes = mutableListOf<Note>()

        while (beatsGenerated < timeSignature.numberOfBeats) {
            val beatStartPoint = beatsGenerated.fractionalPart()
            val maximumDuration = timeSignature.numberOfBeats - beatsGenerated

            val note = generateNote(random, scale, beatStartPoint, maximumDuration)
            notes.add(note)

            hatchet.d("Generated $note")

            beatsGenerated += note
                .duration
                .beats
                .adjustForTimeSignature(timeSignature)
        }

        return Measure(notes)
    }

    private fun generateNote(
        random: Random,
        scale: Scale,
        beatStartPoint: Double, // Hard to name. 0.5 == note starts on & of a beat
        maximumDuration: Double
    ): Note {
        val pitchIndex = random.nextInt(PITCH_INDEX_RANGE)

        val octave = random.nextInt(OCTAVE_RANGE) + OCTAVE_MIN
        val pitch = scale.note(pitchIndex, octave)

        val possibleDurations = Duration
            .values()
            .filter { it.beats <= maximumDuration }
            .filter { it.beats.fractionalPart() - beatStartPoint == 0.0 }
            .toTypedArray()

        val duration = random.nextValue(possibleDurations)
        val amplitude = random.nextDouble(AMPLITUDE_RANGE) + AMPLITUDE_MIN

        return Note(
            pitch,
            duration,
            amplitude
        )
    }

    private fun generateTimeSignature(trackId: Long, random: Random) = when {
        trackId.isDivisibleBy(DIVISOR_FIVE) -> TimeSignature.FIVE
        trackId.isDivisibleBy(DIVISOR_BLUE_RONDO) -> TimeSignature.BLUE_RONDO
        trackId.isDivisibleBy(DIVISOR_UNSQUARE) -> TimeSignature.UNSQUARE
        trackId.isDivisibleBy(2) -> TimeSignature.COMMON
        else -> random.nextValue(arrayOf(TimeSignature.MARCH, TimeSignature.WALTZ))
    }

    private fun Double.adjustForTimeSignature(timeSignature: TimeSignature): Double =
        when (timeSignature.durationOfBeat) {
            Duration.HALF -> this / 2.0

            Duration.QUARTER -> this

            Duration.EIGHTH -> this * 2.0

            Duration.SIXTEENTH -> this * SIXTEENTH_BEAT_MULTIPLIER

            else -> throw IllegalArgumentException(
                "Only time signatures with denominators of 2, 4, 8, or 16 are allowed."
            )
        }

    private fun Double.fractionalPart(): Double {
        val integerPart = floor(this)
        return this - integerPart
    }

    private fun <Return> Random.nextValue(values: Array<Return>): Return {
        val index = nextInt(values.size)
        return values[index]
    }

//        const val PREFER_WHOLE_BEATS = listOf(Duration.WHOLE, Duration.HALF_DOTTED, Duration.)

    private const val TEMPO_RANGE_BPM = 140
    private const val TEMPO_MIN_BPM = 60

    private const val LOOP_LENGTH_SHORT = 8
    private const val LOOP_LENGTH_MEDIUM = 16
    private const val LOOP_LENGTH_LONG = 32

    private const val PITCH_INDEX_RANGE = 6

    private const val OCTAVE_RANGE = 2
    private const val OCTAVE_MIN = 3

    private const val AMPLITUDE_RANGE = 0.3
    private const val AMPLITUDE_MIN = 0.4

    private const val DIVISOR_FIVE = 7
    private const val DIVISOR_BLUE_RONDO = 11
    private const val DIVISOR_UNSQUARE = 13

    private const val SIXTEENTH_BEAT_MULTIPLIER = 4.0
}
