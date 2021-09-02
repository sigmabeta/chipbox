package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.generator.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.generator.fake.models.Note
import net.sigmabeta.chipbox.player.generator.fake.synths.SineSynth

class FakeEmulator(
    private val track: GeneratedTrack,
    private val sampleRate: Int
) {
    private val notes = ArrayDeque<Note>().apply { addAll(track.measures.flatMap { it.notes }) }

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

                framesPlayedForCurrentNote = 0
                remainingFramesForCurrentNote = note
                    .duration
                    .toMsAtTempo(track.tempo)
                    .millisToFrames(sampleRate)
            }

            val currentMillis = framesPlayedForCurrentNote.framesToMillis(sampleRate)
            val rawSample = SineSynth.generate(
                currentMillis,
                note.pitch.frequency,
                note.amplitude
            )

            val adsr = AdsrProcessor.calculateAdsr(
                framesPlayedForCurrentNote,
                framesPlayedForCurrentNote + remainingFramesForCurrentNote
            )

            val sample = (rawSample * adsr).toShortValue()

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