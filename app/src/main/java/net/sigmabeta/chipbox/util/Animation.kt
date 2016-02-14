package net.sigmabeta.chipbox.util

import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

val accelerateInterpolator = AccelerateInterpolator()
val decelerateInterpolator = DecelerateInterpolator()

fun View.slideViewDown(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(accelerateInterpolator)
            .setDuration(300)
            .translationY(height.toFloat())
}

fun View.slideViewUp(): ViewPropertyAnimator {
    visibility = View.VISIBLE

    return animate()
            .withLayer()
            .setInterpolator(decelerateInterpolator)
            .setDuration(300)
            .translationY(0.0f)
}

