package net.sigmabeta.chipbox.player.common

fun Long.millisToFrames(sampleRate: Int) = (this * sampleRate.rateInMillis()).toInt()

fun Long.framesToMillis(sampleRate: Int) = this / (sampleRate.rateInMillis())

fun Int.framesToShorts() = (this * SHORTS_PER_FRAME)

fun Double.toShortValue() = this.toInt().toShort()

fun Int.rateInMillis() = this / MILLIS_PER_SECOND

fun Double.rateInMillis() = this / MILLIS_PER_SECOND

fun Double.millisToSeconds() = this / MILLIS_PER_SECOND

const val MILLIS_PER_SECOND = 1_000.0
const val CHANNELS_STEREO = 2
const val BYTES_PER_SHORT = 2
const val BYTES_PER_SAMPLE = BYTES_PER_SHORT
const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS_STEREO
const val SHORTS_PER_FRAME = BYTES_PER_FRAME / BYTES_PER_SAMPLE
