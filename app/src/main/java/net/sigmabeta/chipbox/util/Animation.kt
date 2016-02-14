package net.sigmabeta.chipbox.util

import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

val accelerateInterpolator = AccelerateInterpolator()
val decelerateInterpolator = DecelerateInterpolator()
val accDecelerateInterpolator = AccelerateDecelerateInterpolator()

fun View.slideViewOffscreen(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(accelerateInterpolator)
            .setDuration(400)
            .translationY(height.toFloat())
}

fun View.slideViewOnscreen(): ViewPropertyAnimator {
    visibility = View.VISIBLE

    return animate()
            .withLayer()
            .setInterpolator(decelerateInterpolator)
            .setDuration(400)
            .translationY(0.0f)
}

fun View.slideViewToProperLocation(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(accDecelerateInterpolator)
            .setDuration(400)
            .translationY(0.0f)
}
