package net.sigmabeta.chipbox.util

import android.content.Context

fun convertDpToPx(original: Float, context: Context): Float {
    val resources = context.resources
    val metrics = resources.displayMetrics

    val pixels = original * metrics.density
    return pixels
}

fun convertPxToDp(original: Float, context: Context): Float {
    val resources = context.resources
    val metrics = resources.displayMetrics

    val dp = original / metrics.density
    return dp
}