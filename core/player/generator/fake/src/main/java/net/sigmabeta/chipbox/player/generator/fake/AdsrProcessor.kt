package net.sigmabeta.chipbox.player.generator.fake

import net.sigmabeta.chipbox.player.common.framesToMillis

sealed class AdsrProcessor {
    abstract fun calculateAdsr(
        currentFrame: Int,
        noteDurationFrames: Int,
        sampleRate: Int
    ): Double

    protected fun calculateAttack(
        noteProgress: Double,
        progressStart: Double,
        progressEnd: Double
    ) =
        doMath(
            noteProgress,
            progressStart,
            progressEnd,
            AMPLITUDE_START,
            AMPLITUDE_ATTACK
        )

    protected fun calculateDecay(noteProgress: Double, progressStart: Double, progressEnd: Double) =
        doMath(
            noteProgress,
            progressStart,
            progressEnd,
            AMPLITUDE_ATTACK,
            AMPLITUDE_SUSTAIN
        )

    protected fun calculateRelease(
        noteProgress: Double,
        progressStart: Double,
        progressEnd: Double
    ) =
        doMath(
            noteProgress,
            progressStart,
            progressEnd,
            AMPLITUDE_SUSTAIN,
            AMPLITUDE_RELEASE
        )

    private fun doMath(
        noteProgress: Double,
        progressStart: Double,
        progressEnd: Double,
        amplitudeStart: Double,
        amplitudeEnd: Double
    ): Double {
        val numeratorLeft = amplitudeStart * (progressEnd - noteProgress)
        val numeratorRight = amplitudeEnd * (noteProgress - progressStart)

        val numerator = numeratorLeft + numeratorRight
        val denominator = progressEnd - progressStart

        return numerator / denominator
    }

    companion object {

        const val AMPLITUDE_START = 0.0
        const val AMPLITUDE_ATTACK = 1.3
        const val AMPLITUDE_SUSTAIN = 1.0
        const val AMPLITUDE_RELEASE = 0.0
    }
}

object PercentAdsrProcessor : AdsrProcessor() {
    override fun calculateAdsr(
        currentFrame: Int,
        noteDurationFrames: Int,
        sampleRate: Int
    ): Double {
        val noteProgress = currentFrame / noteDurationFrames.toDouble()

        return when {
            noteProgress < PROGRESS_ATTACK -> calculateAttack(
                noteProgress,
                PROGRESS_START,
                PROGRESS_ATTACK
            )
            noteProgress < PROGRESS_DECAY -> calculateDecay(
                noteProgress,
                PROGRESS_ATTACK,
                PROGRESS_DECAY
            )
            noteProgress < PROGRESS_SUSTAIN -> AMPLITUDE_SUSTAIN
            noteProgress < PROGRESS_RELEASE -> calculateRelease(
                noteProgress,
                PROGRESS_SUSTAIN,
                PROGRESS_RELEASE
            )
            else -> 0.0
        }
    }

    private const val PROGRESS_START = 0.0
    private const val PROGRESS_ATTACK = 0.01
    private const val PROGRESS_DECAY = 0.03
    private const val PROGRESS_SUSTAIN = 0.90
    private const val PROGRESS_RELEASE = 0.995
}

object TimeAdsrProcessor : AdsrProcessor() {
    override fun calculateAdsr(
        currentFrame: Int,
        noteDurationFrames: Int,
        sampleRate: Int
    ): Double {
        val currentMillis = currentFrame.framesToMillis(sampleRate)
        val noteDurationMillis = noteDurationFrames.framesToMillis(sampleRate)

        val sustainEndMillis = noteDurationMillis - (MILLIS_RELEASE + MILLIS_SILENCE)
        val releaseEndMillis = noteDurationMillis - MILLIS_SILENCE

        return when {
            currentMillis < MILLIS_ATTACK -> calculateAttack(
                currentMillis,
                MILLIS_START,
                MILLIS_ATTACK
            )
            currentMillis < MILLIS_DECAY -> calculateDecay(
                currentMillis,
                MILLIS_ATTACK,
                MILLIS_DECAY
            )
            currentMillis < sustainEndMillis -> AMPLITUDE_SUSTAIN
            currentMillis < releaseEndMillis -> calculateRelease(
                currentMillis,
                sustainEndMillis,
                releaseEndMillis
            )
            else -> 0.0
        }
    }

    private const val PROGRESS_START = 0.0

    private const val MILLIS_START = 0.0
    private const val MILLIS_ATTACK = 20.0
    private const val MILLIS_DECAY = 40.0
    private const val MILLIS_RELEASE = 50.0
    private const val MILLIS_SILENCE = 5.0
}