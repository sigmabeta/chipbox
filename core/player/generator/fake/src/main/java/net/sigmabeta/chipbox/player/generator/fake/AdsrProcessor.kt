package net.sigmabeta.chipbox.player.generator.fake

object AdsrProcessor {
    fun calculateAdsr(currentFrame: Int, noteDurationFrames: Int): Double {
        val noteProgress = currentFrame / noteDurationFrames.toDouble()

        return when {
            noteProgress < PROGRESS_ATTACK -> calculateAttack(noteProgress)
            noteProgress < PROGRESS_DECAY -> calculateDecay(noteProgress)
            noteProgress < PROGRESS_SUSTAIN -> AMPLITUDE_SUSTAIN
            noteProgress < PROGRESS_RELEASE -> calculateRelease(noteProgress)
            else -> 0.0
        }
    }

    private fun calculateAttack(noteProgress: Double) =
        doMath(
            noteProgress,
            PROGRESS_START,
            PROGRESS_ATTACK,
            AMPLITUDE_START,
            AMPLITUDE_ATTACK
        )

    private fun calculateDecay(noteProgress: Double) =
        doMath(
            noteProgress,
            PROGRESS_ATTACK,
            PROGRESS_DECAY,
            AMPLITUDE_ATTACK,
            AMPLITUDE_SUSTAIN
        )

    private fun calculateRelease(noteProgress: Double) =
        doMath(
            noteProgress,
            PROGRESS_SUSTAIN,
            PROGRESS_RELEASE,
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

    private const val PROGRESS_START = 0.0
    private const val PROGRESS_ATTACK = 0.01
    private const val PROGRESS_DECAY = 0.03
    private const val PROGRESS_SUSTAIN = 0.90
    private const val PROGRESS_RELEASE = 0.995

    private const val AMPLITUDE_START = 0.0
    private const val AMPLITUDE_ATTACK = 1.3
    private const val AMPLITUDE_SUSTAIN = 1.0
    private const val AMPLITUDE_RELEASE = 0.0
}
