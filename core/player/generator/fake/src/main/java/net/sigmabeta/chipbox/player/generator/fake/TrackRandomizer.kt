package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.generator.fake.models.*
import net.sigmabeta.chipbox.repository.Repository
import kotlin.random.Random

class TrackRandomizer(private val repository: Repository) {

    fun generate(trackId: Long): GeneratedTrack? {
        val track = repository.getTrack(trackId) ?: return null

        val lengthMs = track.trackLengthMs.toDouble()
        var msGenerated = 0.0

        val random = Random(trackId)

        val tempo = random.nextInt(140) + 60

        val notes = mutableListOf<Note>()

        val root = random.nextValue(PitchClass.values())
        val mode = random.nextValue(ScaleMode.values())
        val scale = Scale(root, mode)

        while (msGenerated < lengthMs) {
            val pitchIndex = random.nextInt(6)

            val octave = random.nextInt(2) + 3
            val pitch = scale.note(pitchIndex, octave)

            val duration = random.nextValue(Duration.values())
            val amplitude = random.nextDouble(0.3) + 0.4

            val note = Note(
                pitch,
                duration,
                amplitude
            )

            notes.add(note)
            msGenerated += duration.toMsAtTempo(tempo)
        }

        return GeneratedTrack(
            trackId,
            lengthMs,
            scale,
            tempo,
            notes
        )
    }
}

fun <Return> Random.nextValue(values: Array<Return>): Return {
    val index = nextInt(values.size)
    return values[index]
}