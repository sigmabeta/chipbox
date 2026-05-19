package net.sigmabeta.chipbox.player.common

import net.sigmabeta.sage.logging.Hatchet
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
 * The product of every registered modification is the *target* gain. The processor does not
 * jump to it: it keeps a private *actual* gain that, applied per frame, steps toward the
 * target by at most [MAX_GAIN_CHANGE_PER_FRAME] each frame. So any external gain change
 * (ducking, master volume, normalization) fades in/out over ~`1 / MAX_GAIN_CHANGE_PER_FRAME`
 * frames rather than clicking. The actual gain persists across buffers, so the ramp continues
 * seamlessly from one [process] call to the next.
 *
 * The effective per-frame gain is that smoothed actual gain times the positional fade gain
 * (the fade is already a per-frame ramp and is left un-smoothed). Each modification is itself
 * capped at [MAX_GAIN]x; results are rounded and clamped to the signed 16-bit range, so a
 * boost (e.g. 150%) is supported without integer wrap-around.
 *
 * A single instance is shared for the lifetime of a [net.sigmabeta.chipbox.player.speaker.Speaker]
 * and mutated from arbitrary threads (audio-focus callbacks land on the main thread; [process]
 * runs on the speaker coroutine), hence the thread-safe registry.
 */
class VolumeProcessor(private val hatchet: Hatchet) {

    private val modifications = ConcurrentHashMap<String, Double>()

    /**
     * Smoothed gain actually applied to audio. Chases the target ([combinedGain]) by at most
     * [MAX_GAIN_CHANGE_PER_FRAME] per frame. Only touched from [process] and [resetGain], both
     * driven by the single speaker coroutine, so it needs no synchronization of its own.
     */
    private var actualGain: Double = 1.0

    /**
     * Snap the smoothed gain back to unity. The speaker calls this when a new track begins so
     * the new track's gain (normalization, plus any active duck/master) ramps in cleanly from
     * 1.0 rather than continuing from the previous track's ramp state.
     */
    fun resetGain() {
        actualGain = 1.0
    }

    /**
     * Register (or replace) the modification stored under [key] with [scale]. `1.0` leaves audio
     * unchanged; `0.5` halves it; `1.5` boosts it by 50%. Clamped to `[0.0, MAX_GAIN]` — a
     * negative scale becomes silence and a boost is capped at [MAX_GAIN]x so a runaway
     * normalization of a near-silent track (or a stray API call) can't blow the output up.
     * Independent of every other key. A change is logged with the resulting combined gain.
     */
    fun setModification(key: String, scale: Double) {
        val clamped = scale.coerceIn(0.0, MAX_GAIN)
        val previous = modifications.put(key, clamped)
        if (previous != clamped) {
            hatchet.d(
                "Volume: '$key' ${fmt(previous ?: 1.0)} -> ${fmt(clamped)} " +
                    "(target gain ${fmt(combinedGain())}, actual gain ${fmt(actualGain)})."
            )
        }
    }

    /** Remove the modification under [key], if any. Other modifications are unaffected. */
    fun clearModification(key: String) {
        val previous = modifications.remove(key)
        if (previous != null) {
            hatchet.d(
                "Volume: cleared '$key' (was ${fmt(previous)}; " +
                    "target gain ${fmt(combinedGain())}, actual gain ${fmt(actualGain)})."
            )
        }
    }

    /** Product of every registered modification (the constant, non-fade component of gain). */
    private fun combinedGain(): Double =
        modifications.values.fold(1.0) { acc, scale -> acc * scale }

    private fun fmt(value: Double): String = "%.3f".format(value)

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
        val targetGain = combinedGain()

        val audioInputLengthMillis = audioInput
            .size
            .samplesToFrames()
            .framesToMillis(sampleRate)

        val fadeActive = fadeLengthMillis > 0 &&
            inputStartMillis + audioInputLengthMillis >= fadeStartMillis

        // Nothing modifies the audio and there's no ramp in progress: leave the buffer as-is.
        if (!fadeActive && targetGain == 1.0 && actualGain == 1.0) return

        val inputStartFrames = inputStartMillis.millisToFrames(sampleRate)
        val fadeStartFrames = fadeStartMillis.millisToFrames(sampleRate)
        val fadeLengthFrames = fadeLengthMillis.millisToFrames(sampleRate)

        for (sampleIndex in audioInput.indices step SHORTS_PER_FRAME) {
            val fadeGain = if (fadeActive) {
                val currentFrame = sampleIndex.samplesToFrames() + inputStartFrames
                val fadeFramesRemaining = fadeStartFrames
                    .plus(fadeLengthFrames)
                    .minus(currentFrame)
                    .toDouble()
                if (fadeFramesRemaining > 0) fadeFramesRemaining / fadeLengthFrames else 0.0
            } else {
                1.0
            }

            val gain = actualGain * fadeGain
            audioInput[sampleIndex] = scaleSample(audioInput[sampleIndex], gain)
            audioInput[sampleIndex + 1] = scaleSample(audioInput[sampleIndex + 1], gain)

            // Step the smoothed gain toward the target after applying this frame, so a change
            // takes ~1 / MAX_GAIN_CHANGE_PER_FRAME frames to fully land (fading it in/out).
            actualGain = approach(actualGain, targetGain, MAX_GAIN_CHANGE_PER_FRAME)
        }
    }

    /** [current] moved toward [target] by at most [maxStep]; snaps exactly to [target] once
     *  within one step so the ramp terminates cleanly (no float drift). */
    private fun approach(current: Double, target: Double, maxStep: Double): Double {
        val delta = target - current
        return when {
            delta > maxStep -> current + maxStep
            delta < -maxStep -> current - maxStep
            else -> target
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

        /** Upper bound on any single modification's gain. Caps boosts so
         *  normalizing a near-silent track can't amplify its noise floor without limit. */
        const val MAX_GAIN = 3.0

        /** Most the applied gain may move toward the target per frame. At 0.01 a full
         *  0.0↔1.0 swing takes 100 frames (~2 ms @ 48 kHz), enough to declick any
         *  ducking/master/normalization change without an audible slew. */
        const val MAX_GAIN_CHANGE_PER_FRAME = 0.001
    }
}
