package net.sigmabeta.chipbox.player.emulators.fake

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.fake.models.GeneratedTrack
import net.sigmabeta.chipbox.player.emulators.fake.models.Note
import net.sigmabeta.chipbox.player.emulators.fake.synths.SineSynth

object FakeEmulator : Emulator() {
    override fun getSampleRateInternal() = 44100

    private var generatedTrack: GeneratedTrack? = null

    private var notes: ArrayDeque<Note>? = null

    private var currentNote: Note? = null

    private val squareSynth = SineSynth

    private var framesPlayedForCurrentNote = 0

    private var remainingFramesForCurrentNote = 0

    override val supportedFileExtensions = emptyList<String>()

    override fun isFileExtensionSupported(extension: String) = true

    override fun loadNativeLib()  = Unit

    override fun loadTrack(track: Track) {
        if (remainingFramesTotal >= 0) {
            println("Previously loaded emulator not cleared. Clearing...")
            teardown()
        }

        remainingFramesTotal =
            track.trackLengthMs.toDouble().millisToFrames(getSampleRateInternal())

        val generatedTrack = TrackRandomizer.generate(track)

        this.generatedTrack = generatedTrack
        notes = ArrayDeque<Note>().apply { addAll(generatedTrack.measures.flatMap { it.notes }) }
    }

    override fun loadTrackInternal(path: String) = Unit

    override fun generateBufferInternal(
        buffer: ShortArray,
        framesPerBuffer: Int,
    ): Int {
        var framesPlayed = 0
        for (currentFrame in 0 until framesPerBuffer) {
            var note = currentNote
            if (note == null || remainingFramesForCurrentNote <= 0) {
                note = notes?.removeFirstOrNull()
                if (note == null) {
                    trackOver = true
                    return framesPlayed
                }

                println("Begin playback of $note")
                currentNote = note

                framesPlayedForCurrentNote = 0
                remainingFramesForCurrentNote = note
                    .duration
                    .toMsAtTempo(generatedTrack?.tempo ?: return -1)
                    .millisToFrames(getSampleRateInternal())
            }

            val currentMillis = framesPlayedForCurrentNote.framesToMillis(getSampleRateInternal())
            val rawSample = squareSynth.generate(
                currentMillis,
                note.pitch.frequency,
                note.amplitude
            )

            val adsr = TimeAdsrProcessor.calculateAdsr(
                framesPlayedForCurrentNote,
                framesPlayedForCurrentNote + remainingFramesForCurrentNote,
                getSampleRateInternal()
            )

            val sample = (rawSample * adsr).toShortValue()

            for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                buffer[(currentFrame.framesToSamples() + sampleOffset)] = sample
            }

            framesPlayed++
            framesPlayedForCurrentNote++
            remainingFramesForCurrentNote--
        }

        return framesPlayed
    }

    override fun teardownInternal() {
        notes = null
        generatedTrack = null
        currentNote = null

        remainingFramesForCurrentNote = 0
        framesPlayedForCurrentNote = 0
    }

    override fun getLastError(): String? = null
}
