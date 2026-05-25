package net.sigmabeta.chipbox.utils

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Format [value] with a fixed number of [decimals] — a multiplatform stand-in for
 * `String.format("%.Nf")`, which isn't in the common stdlib. Rounds half-up on magnitude;
 * [decimals] <= 0 yields the rounded integer with no decimal point.
 */
fun formatDecimal(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = (abs(value) * factor).roundToLong()
    val intPart = rounded / factor
    val fracPart = rounded % factor
    val sign = if (value < 0 && rounded != 0L) "-" else ""
    return if (decimals <= 0) {
        "$sign$intPart"
    } else {
        "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
    }
}
