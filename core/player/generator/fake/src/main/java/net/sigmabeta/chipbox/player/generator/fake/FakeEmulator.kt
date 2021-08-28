package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.common.framesToShorts
import net.sigmabeta.chipbox.player.common.millisToFrames
import net.sigmabeta.chipbox.player.generator.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.generator.fake.models.Note
import net.sigmabeta.chipbox.player.generator.fake.synths.SineSynth

class FakeEmulator(
    private val track: GeneratedTrack,
    private val sampleRate: Int
) {
    private val notes = ArrayDeque<Note>().apply { addAll(track.notes) }

    var trackOver = false

    private var currentNote: Note? = null

    private var framesPlayedForCurrentNote = 0

    private var remainingFramesForCurrentNote = 0

    fun generateBuffer(
        buffer: ShortArray
    ): Int {
        val framesPerBuffer = buffer.size / SHORTS_PER_FRAME

        var remainingFrames = framesPerBuffer
        var framesPlayed = 0

        for (currentFrame in 0 until framesPerBuffer) {
            if (remainingFrames <= 0) {
                clearRemainingBufferSpace(buffer, currentFrame)
                continue
            }

            var note = currentNote
            if (note == null || remainingFramesForCurrentNote <= 0) {
                note = notes.removeFirstOrNull()
                if (note == null) {
                    trackOver = true
                    break
                }

                currentNote = note
                remainingFramesForCurrentNote = note.durationMillis.millisToFrames(sampleRate)
            }

            val currentMillis = framesPlayedForCurrentNote.framesToMillis(sampleRate)
            val sample = SineSynth.generate(
                currentMillis,
                note.pitch.frequency,
                note.amplitude
            )

            for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                buffer[(currentFrame.framesToShorts() + sampleOffset)] = sample
            }

            framesPlayed++
            framesPlayedForCurrentNote++
            remainingFrames--
            remainingFramesForCurrentNote--
        }

        return framesPlayed
    }

    private fun clearRemainingBufferSpace(buffer: ShortArray, currentFrame: Int) {
        for (sampleOffset in 0 until SHORTS_PER_FRAME) {
            buffer[(currentFrame.framesToShorts() + sampleOffset)] = 0
        }
    }
}