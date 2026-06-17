package net.sigmabeta.chipbox.player.common

import net.sigmabeta.sage.logging.Hatchet
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
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
@OptIn(ExperimentalAtomicApi::class)
class VolumeProcessor(private val hatchet: Hatchet) {

    /**
     * Keyed gain multipliers, held as an immutable map behind an atomic reference so the registry
     * stays thread-safe across platforms without `java.util.concurrent`: writers swap in a new map
     * via compare-and-set ([mutateModifications]); readers ([combinedGain], [debugSnapshot]) load a
     * consistent immutable snapshot.
     */
    private val modifications = AtomicReference<Map<String, Double>>(emptyMap())

    /** Atomically replace the registry via [transform], returning the map as it was before. */
    private inline fun mutateModifications(
        transform: (Map<String, Double>) -> Map<String, Double>,
    ): Map<String, Double> {
        while (true) {
            val current = modifications.load()
            if (modifications.compareAndSet(current, transform(current))) return current
        }
    }

    /**
     * Smoothed gain actually applied to audio. Chases the target ([combinedGain]) by at most
     * [MAX_GAIN_CHANGE_PER_FRAME] per frame. Only touched from [process] and [resetGain], both
     * driven by the single speaker coroutine, so it needs no synchronization of its own.
     */
    private var actualGain: Double = STARTING_GAIN

    /**
     * When false, [setNormalization] is a no-op and any active normalization gain is cleared, so
     * tracks play at their raw measured loudness. When true (default), normalization is applied.
     * Driven live by the director from the user setting. Read on the speaker coroutine but written
     * from the director coroutine, hence [Volatile].
     */
    @Volatile
    private var normalizationEnabled: Boolean = true

    /**
     * When false, [resetGain] snaps straight to unity so a new track starts at full volume with no
     * fade-in ramp; when true (default), it starts from [STARTING_GAIN] and ramps up. Driven live
     * by the director from the user setting. [Volatile] for the same cross-coroutine reason as
     * [normalizationEnabled].
     */
    @Volatile
    private var fadeInEnabled: Boolean = true

    /**
     * The last (LUFS, dBTP) the speaker reported via [setNormalization], retained so re-enabling
     * normalization mid-track can re-derive the *current* track's gain immediately instead of
     * waiting for the next track. Written on the speaker coroutine, read on the director coroutine
     * (from [setNormalizationEnabled]), so it's held behind an atomic for a consistent pair.
     * Starts as (NaN, NaN) — the "nothing measured yet" sentinel, which [normalizationGain] maps to
     * unity.
     */
    private val lastNormalizationInput = AtomicReference(NormalizationInput(Double.NaN, Double.NaN))

    /**
     * Snap the smoothed gain back to its track-start value. The speaker calls this when a new track
     * begins so the new track's gain (normalization, plus any active duck/master) ramps in cleanly
     * rather than continuing from the previous track's ramp state. With the fade-in disabled this
     * starts at unity (no ramp); otherwise it starts at [STARTING_GAIN] and ramps up.
     */
    fun resetGain() {
        actualGain = if (fadeInEnabled) STARTING_GAIN else 1.0
    }

    /**
     * Enable or disable loudness normalization. Both directions take effect on the current track:
     * disabling clears the normalization gain and the smoothed gain ramps back toward the raw level;
     * enabling re-derives the gain from the last measured loudness ([lastNormalizationInput]) and
     * ramps into it. With no measurement yet (e.g. before the first buffer) the derived gain is
     * unity, so enabling is a no-op until loudness arrives. Independent of the fade, ducking, and
     * master volume.
     */
    fun setNormalizationEnabled(enabled: Boolean) {
        normalizationEnabled = enabled
        if (enabled) {
            val input = lastNormalizationInput.load()
            setModification(KEY_NORMALIZATION, normalizationGain(input.loudnessLufs, input.truePeakDbtp))
        } else {
            clearModification(KEY_NORMALIZATION)
        }
    }

    /**
     * Enable or disable the start-of-track fade-in — the [STARTING_GAIN]→unity ramp [resetGain]
     * arms. Takes effect at the next track start.
     */
    fun setFadeInEnabled(enabled: Boolean) {
        fadeInEnabled = enabled
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
        mutateModifications { it + (key to clamped) }[key]
    }

    /** Remove the modification under [key], if any. Other modifications are unaffected. */
    fun clearModification(key: String) {
        val previous = mutateModifications { it - key }[key]
        if (previous != null) {
            hatchet.d(
                "Volume: cleared '$key' (was ${fmt(previous)}; " +
                    "target gain ${fmt(combinedGain())}, actual gain ${fmt(actualGain)})."
            )
        }
    }

    /** Product of every registered modification (the constant, non-fade component of gain). */
    private fun combinedGain(): Double =
        modifications.load().values.fold(1.0) { acc, scale -> acc * scale }

    /**
     * Observational snapshot for the debug PlaybackStatus screen: the target gain (product of
     * every modification), the smoothed gain currently applied, the per-modification cap, and a
     * copy of the keyed multipliers. Safe to call from any thread.
     */
    fun debugSnapshot(): VolumeDebugInfo = VolumeDebugInfo(
        targetGain = combinedGain(),
        actualGain = actualGain,
        maxGain = MAX_GAIN,
        modifications = modifications.load(),
    )

    // Fixed 3-decimal formatting for debug logs. The common stdlib has no String.format, so round
    // to milli-units and split into integer + zero-padded fractional parts by hand.
    private fun fmt(value: Double): String {
        val milliUnits = 1_000
        val decimals = 3
        val rounded = (abs(value) * milliUnits).roundToInt()
        val sign = if (value < 0 && rounded != 0) "-" else ""
        return "$sign${rounded / milliUnits}.${(rounded % milliUnits).toString().padStart(decimals, '0')}"
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
     * Convenience wrapper: loudness-normalize the current track from its measured integrated
     * loudness [loudnessLufs] and true peak [truePeakDbtp]. Non-finite [loudnessLufs]
     * (`Double.NaN`) means "unknown — leave audio unchanged"; a non-finite [truePeakDbtp]
     * drops the peak ceiling and uses pure loudness gain. Independent of the fade-out,
     * ducking, and master volume. Set per buffer by the speaker — for a render-ahead source
     * the measurement climbs over the first 400 ms then settles, so the gain re-derives until
     * stable.
     */
    fun setNormalization(loudnessLufs: Double, truePeakDbtp: Double) {
        // Remember the measurement even while disabled, so a later enable can re-derive this track's
        // gain without waiting for the next track to report loudness again.
        lastNormalizationInput.store(NormalizationInput(loudnessLufs, truePeakDbtp))
        if (!normalizationEnabled) return
        setModification(KEY_NORMALIZATION, normalizationGain(loudnessLufs, truePeakDbtp))
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

            // Step the smoothed gain toward the target after applying this frame. The ramp is
            // asymmetric — slow attack, fast decay — so the per-frame step depends on whether
            // gain is rising or falling. See MAX_GAIN_CHANGE_PER_FRAME_UP/DOWN below.
            actualGain = approach(actualGain, targetGain)
        }
    }

    /** [current] moved toward [target] by an asymmetric per-frame step — UP when rising,
     *  DOWN when falling — and snapped exactly to [target] once within one step so the ramp
     *  terminates cleanly (no float drift). */
    private fun approach(current: Double, target: Double): Double {
        val delta = target - current

        val maxStepUp = MAX_GAIN_CHANGE_PER_FRAME_UP
        val maxStepDown = MAX_GAIN_CHANGE_PER_FRAME_DOWN

        return when {
            delta > maxStepUp -> current + maxStepUp
            delta < -maxStepDown -> current - maxStepDown
            else -> target
        }
    }

    private fun scaleSample(sample: Short, gain: Double): Short =
        (sample * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    /** Immutable (LUFS, dBTP) pair, stored atomically so the speaker-thread writer and
     *  director-thread reader always see a consistent measurement. */
    private data class NormalizationInput(val loudnessLufs: Double, val truePeakDbtp: Double)

    companion object {
        /** Track-start gain the fade-in ramps up from (when enabled via [setFadeInEnabled]). */
        const val STARTING_GAIN = 0.5

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
        const val MAX_GAIN = 5.0

        /** Per-frame cap on how far the applied gain may move toward the target, split
         *  asymmetrically: rises (attack) crawl up at 0.00005/frame — a 0.0→1.0 swing takes
         *  ~20000 frames (~417 ms @ 48 kHz), slow enough that normalization or unducking
         *  doesn't pump on quiet sections. Falls (decay) bite at 0.0001/frame — a 1.0→0.0
         *  swing takes ~10000 frames (~208 ms), fast enough that ducks land and peak-limit
         *  cuts land before something audibly clips. */
        const val MAX_GAIN_CHANGE_PER_FRAME_UP = 0.00005
        const val MAX_GAIN_CHANGE_PER_FRAME_DOWN = 0.0001
    }
}
