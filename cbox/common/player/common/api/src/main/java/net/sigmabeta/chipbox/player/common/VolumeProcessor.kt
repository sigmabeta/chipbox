package net.sigmabeta.chipbox.player.common

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Applies volume adjustments in place to stereo 16-bit PCM buffers on the consumer side of the
 * playback pipeline.
 *
 * Two kinds of adjustment are combined, independently of one another:
 *
 *  1. **End-of-track fade-out** — positional, derived per-buffer from the track's declared length
 *     and fade window (most chiptune emulators loop forever, so the player schedules a fade rather
 *     than relying on an "end of file"). The gain ramps linearly from 1.0 to 0.0 across the fade
 *     window and clamps to 0.0 past the end so any extra samples the emulator hands back are
 *     silent.
 *
 *  2. **Persistent modifications** — a keyed registry of constant gain multipliers, each fully
 *     independent of the others and of the fade. OS-driven ducking ([setDucked]) and an arbitrary
 *     user/master volume ([setMasterVolume]) are convenience wrappers over the generic
 *     [setModification] / [clearModification] pair; additional modifications (e.g. an equalizer
 *     pre-gain) can be added under their own keys without touching existing ones.
 *
 * The effective per-frame gain is the product of the fade gain and every registered
 * modification. Results are rounded and clamped to the signed 16-bit range, so a modification
 * greater than 1.0 (e.g. boosting to 150%) is supported without integer wrap-around.
 *
 * A single instance is shared for the lifetime of a [net.sigmabeta.chipbox.player.speaker.Speaker]
 * and mutated from arbitrary threads (audio-focus callbacks land on the main thread; [process]
 * runs on the speaker coroutine), hence the thread-safe registry.
 */
class VolumeProcessor {

    private val modifications = ConcurrentHashMap<String, Double>()

    /**
     * Register (or replace) the modification stored under [key] with [scale]. `1.0` leaves audio
     * unchanged; `0.5` halves it; `1.5` boosts it by 50%. Negative values are clamped to `0.0`.
     * Independent of every other key.
     */
    fun setModification(key: String, scale: Double) {
        modifications[key] = scale.coerceAtLeast(0.0)
    }

    /** Remove the modification under [key], if any. Other modifications are unaffected. */
    fun clearModification(key: String) {
        modifications.remove(key)
    }

    /**
     * Convenience wrapper: duck output to [DUCK_SCALE] while [ducked], restoring full volume
     * (for this modification) when not. Used to respond to transient OS audio-focus loss.
     */
    fun setDucked(ducked: Boolean) {
        if (ducked) setModification(KEY_DUCK, DUCK_SCALE) else clearModification(KEY_DUCK)
    }

    /**
     * Convenience wrapper: set an arbitrary master output [scale] (e.g. `1.5` for 150%).
     * Independent of the fade-out and of ducking.
     */
    fun setMasterVolume(scale: Double) {
        setModification(KEY_MASTER, scale)
    }

    /**
     * Convenience wrapper: peak-normalize the current track from its measured loudest sample
     * [peakAmplitude] (0 = unknown, leaves audio unchanged). Independent of the fade-out,
     * ducking, and master volume. Set once per track by the speaker.
     */
    fun setNormalization(peakAmplitude: Int) {
        setModification(KEY_NORMALIZATION, normalizationGain(peakAmplitude))
    }

    /**
     * Mutate [audioInput] in place, applying the end-of-track fade-out (if this buffer reaches
     * into the fade window) and every registered modification.
     *
     * @param audioInput Stereo PCM buffer; mutated in place.
     * @param sampleRate Buffer sample rate, Hz.
     * @param inputStartMillis Position of [audioInput]'s first frame within the track.
     * @param fadeStartMillis Position at which the fade should begin (typically
     *        `trackLengthMs`). A non-positive [fadeLengthMillis] disables the fade.
     * @param fadeLengthMillis Duration of the fade ramp.
     */
    fun process(
        audioInput: ShortArray,
        sampleRate: Int,
        inputStartMillis: Double,
        fadeStartMillis: Double,
        fadeLengthMillis: Double,
    ) {
        val staticGain = modifications.values.fold(1.0) { acc, scale -> acc * scale }

        val audioInputLengthMillis = audioInput
            .size
            .samplesToFrames()
            .framesToMillis(sampleRate)

        val fadeActive = fadeLengthMillis > 0 &&
            inputStartMillis + audioInputLengthMillis >= fadeStartMillis

        if (!fadeActive) {
            // No positional component — apply the (constant) static gain uniformly, or skip
            // entirely when nothing modifies the audio.
            if (staticGain == 1.0) return
            for (sampleIndex in audioInput.indices) {
                audioInput[sampleIndex] = scaleSample(audioInput[sampleIndex], staticGain)
            }
            return
        }

        val inputStartFrames = inputStartMillis.millisToFrames(sampleRate)
        val fadeStartFrames = fadeStartMillis.millisToFrames(sampleRate)
        val fadeLengthFrames = fadeLengthMillis.millisToFrames(sampleRate)

        for (sampleIndex in audioInput.indices step SHORTS_PER_FRAME) {
            val currentFrame = sampleIndex.samplesToFrames() + inputStartFrames

            val fadeFramesRemaining = fadeStartFrames
                .plus(fadeLengthFrames)
                .minus(currentFrame)
                .toDouble()

            val fadeGain = if (fadeFramesRemaining > 0) {
                fadeFramesRemaining / fadeLengthFrames
            } else {
                0.0
            }

            val gain = staticGain * fadeGain
            audioInput[sampleIndex] = scaleSample(audioInput[sampleIndex], gain)
            audioInput[sampleIndex + 1] = scaleSample(audioInput[sampleIndex + 1], gain)
        }
    }

    private fun scaleSample(sample: Short, gain: Double): Short =
        (sample * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    companion object {
        /** Registry key for OS-driven transient ducking. */
        const val KEY_DUCK = "duck"

        /** Registry key for the arbitrary user/master volume. */
        const val KEY_MASTER = "master"

        /** Registry key for per-track peak normalization. */
        const val KEY_NORMALIZATION = "normalization"

        /** Gain applied while ducked (50%). */
        const val DUCK_SCALE = 0.5
    }
}
