package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.generator.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.generator.fake.models.Note
import net.sigmabeta.chipbox.player.generator.fake.synths.SquareSynth

class FakeEmulator(
    private val track: GeneratedTrack,
    private val sampleRate: Int
) {
    private val notes = ArrayDeque<Note>().apply { addAll(track.measures.flatMap { it.notes }) }

    var trackOver = false

    private var currentNote: Note? = null

    private var framesPlayedTotal = 0

    private var remainingFramesTotal = track.trackLengthMs.millisToFrames(sampleRate)

    private var framesPlayedForCurrentNote = 0

    private var remainingFramesForCurrentNote = 0

    private val squareSynth = SquareSynth(0.25)

    fun generateBuffer(
        buffer: ShortArray
    ): Int {
        val framesPerBuffer = buffer.size / SHORTS_PER_FRAME

        var framesPlayed = 0

        for (currentFrame in 0 until framesPerBuffer) {
            if (remainingFramesTotal <= 0) {
                trackOver = true
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
            val rawSample = squareSynth.generate(
                currentMillis,
                note.pitch.frequency,
                note.amplitude
            )

            val adsr = TimeAdsrProcessor.calculateAdsr(
                framesPlayedForCurrentNote,
                framesPlayedForCurrentNote + remainingFramesForCurrentNote,
                sampleRate
            )

            val sample = (rawSample * adsr).toShortValue()

            for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                buffer[(currentFrame.framesToShorts() + sampleOffset)] = sample
            }

            framesPlayed++
            framesPlayedTotal++
            framesPlayedForCurrentNote++
            remainingFramesTotal--
            remainingFramesForCurrentNote--
        }

        return framesPlayed
    }
}