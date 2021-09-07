package net.sigmabeta.chipbox.player.generator

import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.common.millisToFrames
import net.sigmabeta.chipbox.player.common.samplesToFrames

object FadeProcessor {
    fun fadeIfNecessary(
        audioInput: ShortArray,
        sampleRate: Int,
        inputStartMillis: Double,
        fadeStartMillis: Double,
        fadeLengthMillis: Double
    ) {
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