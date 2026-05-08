package net.sigmabeta.chipbox.ui.components.utils

import com.google.android.material.math.MathUtils
import kotlin.random.Random

fun partialLerpUntil(
    start: Float,
    end: Float,
    progress: Float,
    threshold: Float,
): Float = if (progress < threshold) {
        start
    } else {
        val scaledProgress = (progress - threshold) / threshold
        MathUtils.lerp(start, end, scaledProgress)
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
        MathUtils.lerp(start, end, scaledProgress)
    }

@Suppress("MagicNumber")
fun Random.nextPercentageFloat(
    minOutOfHundred: Int,
    maxOutOfHundred: Int = 100,
) = nextInt(maxOutOfHundred - minOutOfHundred)
    .plus(minOutOfHundred)
    .div(100f)
