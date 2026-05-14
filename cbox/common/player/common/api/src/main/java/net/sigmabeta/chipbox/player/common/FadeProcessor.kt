package net.sigmabeta.chipbox.player.common

/**
 * Applies a linear volume fade-out in place to PCM buffers as a track approaches its end.
 *
 * Most chiptune emulators loop forever — there is no "end of file" — so the player schedules a
 * fade based on the track's declared length. This processor multiplies each sample by a scale
 * factor that ramps from 1.0 down to 0.0 across the configured fade window, then clamps to 0.0
 * past the end so any extra samples the emulator hands back are silent.
 */
object FadeProcessor {
    /**
     * @param audioInput Stereo PCM buffer; mutated in place.
     * @param sampleRate Buffer sample rate, Hz.
     * @param inputStartMillis Position of [audioInput]'s first frame within the track.
     * @param fadeStartMillis Position at which the fade should begin (typically
     *        `trackLengthMs - fadeLengthMillis`).
     * @param fadeLengthMillis Duration of the fade ramp.
     */
    fun fadeIfNecessary(
        audioInput: ShortArray,
        sampleRate: Int,
        inputStartMillis: Double,
        fadeStartMillis: Double,
        fadeLengthMillis: Double
    ) {
        if (fadeLengthMillis <= 0) return

        val audioInputLengthMillis = audioInput
            .size
            .samplesToFrames()
            .framesToMillis(sampleRate)

        if (inputStartMillis + audioInputLengthMillis < fadeStartMillis) {
            return
        }

        for (sampleIndex in audioInput.indices step 2) {
            val frameIndex = sampleIndex.samplesToFrames()
            val inputStartFrames = inputStartMillis.millisToFrames(sampleRate)

            val currentFrame = frameIndex + inputStartFrames

            val fadeStartFrames = fadeStartMillis.millisToFrames(sampleRate)
            val fadeLengthFrames = fadeLengthMillis.millisToFrames(sampleRate)

            val fadeFramesRemaining = fadeStartFrames
                .plus(fadeLengthFrames)
                .minus(currentFrame)
                .toDouble()

            val sampleScaleFactor = if (fadeFramesRemaining > 0) {
                fadeFramesRemaining / fadeLengthFrames
            } else {
                0.0
            }

            fadeSampleAtIndex(audioInput, sampleScaleFactor, sampleIndex)
            fadeSampleAtIndex(audioInput, sampleScaleFactor, sampleIndex + 1)
        }
    }

    private fun fadeSampleAtIndex(
        audioInput: ShortArray,
        sampleScaleFactor: Double,
        sampleIndex: Int
    ) {
        audioInput[sampleIndex] = (audioInput[sampleIndex] * sampleScaleFactor).toInt().toShort()
    }
}
