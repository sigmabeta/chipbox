package net.sigmabeta.chipbox.ui.components.api.utils

import kotlin.random.Random

private fun lerp(start: Float, end: Float, t: Float): Float = start + t * (end - start)

fun partialLerpUntil(
    start: Float,
    end: Float,
    progress: Float,
    threshold: Float,
): Float = if (progress < threshold) {
        start
    } else {
        val scaledProgress = (progress - threshold) / threshold
        lerp(start, end, scaledProgress)
    }

fun partialLerpAfter(
    start: Float,
    end: Float,
    progress: Float,
    threshold: Float,
): Float = if (progress > threshold) {
        end
    } else {
        val scaledProgress = 1.0f - ((threshold - progress) / threshold)
        lerp(start, end, scaledProgress)
    }

@Suppress("MagicNumber")
fun Random.nextPercentageFloat(
    minOutOfHundred: Int,
    maxOutOfHundred: Int = 100,
) = nextInt(maxOutOfHundred - minOutOfHundred)
    .plus(minOutOfHundred)
    .div(100f)
