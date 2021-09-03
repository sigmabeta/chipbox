package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.common.isDivisibleBy
import net.sigmabeta.chipbox.player.generator.fake.models.*
import net.sigmabeta.chipbox.repository.Repository
import kotlin.math.floor
import kotlin.random.Random

class TrackRandomizer(private val repository: Repository) {

    fun generate(trackId: Long): GeneratedTrack? {
        val track = repository.getTrack(trackId) ?: return null

        val lengthMs = track.trackLengthMs.toDouble()
        var msGenerated = 0.0
        var measuresGenerated = 0
        var loops = 0

        val random = Random(trackId)

        val tempo = random.nextInt(140) + 60
        val timeSignature = generateTimeSignature(trackId, random)

        val generatedMeasures = mutableListOf<Measure>()

        val root = random.nextValue(PitchClass.values())
        val mode = random.nextValue(ScaleMode.values())
        val scale = Scale(root, mode)

        val measuresInLoop = random.nextValue(arrayOf(8, 16, 32))
        while (measuresGenerated < measuresInLoop) {
            val measure = generateMeasure(random, timeSignature, scale)
            generatedMeasures.add(measure)
            println("Generated $measure")
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

                println("Adding measure with length $measureDuration")
                println("Total song generated: $msGenerated / $lengthMs")
                if (msGenerated >= lengthMs) {
                    break
                }
            }

            println("Looping for the $loops time")
            loops++

            if (msGenerated >= lengthMs) break
        }

        return GeneratedTrack(
            trackId,
            lengthMs,
            scale,
            timeSignature,
            tempo,
            generatedMeasures
        )
    }

    private fun generateMeasure(
        random: Random,
        timeSignature: TimeSignature,
        scale: Scale
    ): Measure {
        var beatsGenerated = 0.0
        val notes = mutableListOf<Note>()

        while (beatsGenerated < timeSignature.numberOfBeats) {
            val beatStartPoint = beatsGenerated.fractionalPart()
            val maximumDuration = timeSignature.numberOfBeats - beatsGenerated

            val note = generateNote(random, scale, beatStartPoint, maximumDuration)
            notes.add(note)

            println("Generated $note")

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
        val pitchIndex = random.nextInt(6)

        val octave = random.nextInt(2) + 3
        val pitch = scale.note(pitchIndex, octave)

        val possibleDurations = Duration
            .values()
            .filter { it.beats <= maximumDuration }
            .filter { it.beats.fractionalPart() - beatStartPoint == 0.0 }
            .toTypedArray()

        val duration = random.nextValue(possibleDurations)
        val amplitude = random.nextDouble(0.3) + 0.4

        return Note(
            pitch,
            duration,
            amplitude
        )
    }

    private fun generateTimeSignature(trackId: Long, random: Random) = when {
        trackId.isDivisibleBy(7) -> TimeSignature.FIVE
        trackId.isDivisibleBy(11) -> TimeSignature.BLUE_RONDO
        trackId.isDivisibleBy(13) -> TimeSignature.UNSQUARE
        trackId.isDivisibleBy(2) -> TimeSignature.COMMON
        else -> random.nextValue(arrayOf(TimeSignature.MARCH, TimeSignature.WALTZ))
    }

    private fun Double.adjustForTimeSignature(timeSignature: TimeSignature): Double {
        return when (timeSignature.durationOfBeat) {
            Duration.HALF -> this / 2.0
            Duration.QUARTER -> this
            Duration.EIGHTH -> this * 2.0
            Duration.SIXTEENTH -> this * 4.0
            else -> throw IllegalArgumentException(
                "Only time signatures with denominators of 2, 4, 8, or 16 are allowed."
            )
        }
    }

    private fun Double.fractionalPart(): Double {
        val integerPart = floor(this)
        return this - integerPart
    }

    private fun <Return> Random.nextValue(values: Array<Return>): Return {
        val index = nextInt(values.size)
        return values[index]
    }

    companion object {
//        const val PREFER_WHOLE_BEATS = listOf(Duration.WHOLE, Duration.HALF_DOTTED, Duration.)
    }
}


