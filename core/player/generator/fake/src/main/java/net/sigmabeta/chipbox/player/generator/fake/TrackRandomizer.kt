package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.generator.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.generator.fake.models.Note
import net.sigmabeta.chipbox.player.generator.fake.models.Pitch
import net.sigmabeta.chipbox.player.generator.fake.models.PitchClass
import net.sigmabeta.chipbox.repository.Repository
import kotlin.random.Random

class TrackRandomizer(private val repository: Repository) {

    fun generate(trackId: Long): GeneratedTrack? {
        val track = repository.getTrack(trackId) ?: return null

        val lengthMs = track.trackLengthMs.toDouble()
        var msGenerated = 0.0

        val random = Random(trackId)
        val notes = mutableListOf<Note>()

        while (msGenerated < lengthMs) {
            val possiblePitchClasses = PitchClass.values()
            val pitchIndex = random.nextInt(possiblePitchClasses.size)

            val octave = random.nextInt(3) + 3
            val pitchClass = possiblePitchClasses[pitchIndex]

            val pitch = Pitch(
                pitchClass,
                octave
            )

            val duration = random.nextDouble(3_000.0) + 500.0
            val amplitude = random.nextDouble(0.3) + 0.3

            val note = Note(
                pitch,
                duration,
                amplitude
            )

            notes.add(note)
            msGenerated += duration
        }

        return GeneratedTrack(
            trackId,
            lengthMs,
            notes
        )
    }
}