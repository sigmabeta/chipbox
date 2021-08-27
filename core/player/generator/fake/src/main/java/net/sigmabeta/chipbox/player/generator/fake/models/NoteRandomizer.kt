package net.sigmabeta.chipbox.player.generator.fake.models

import java.util.*

object NoteRandomizer {
    fun randomNote(trackId: Long): Note {
        val random = Random(trackId)

        return Note(
            Pitch(
                PitchClass.values()[random.nextInt(12)],
                random.nextInt(4) + 2
            ),
            random.nextInt(5_000) + 2_000L
        )
    }
}