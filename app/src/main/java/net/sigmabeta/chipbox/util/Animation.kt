package net.sigmabeta.chipbox.util

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

val SCROLL_DIRECTION_DOWN = 1
val SCROLL_DIRECTION_UP = -1

fun RecyclerView.isScrolledToBottom(): Boolean {
    val canScrollDown = canScrollVertically(SCROLL_DIRECTION_DOWN)
    val canScrollUp = canScrollVertically(SCROLL_DIRECTION_UP)

    logVerbose("[RecyclerViewExtension] ScrollUp ${canScrollUp} ScrollDown ${canScrollDown}")

    return !canScrollDown && canScrollUp
}

val ACCELERATE = AccelerateInterpolator()
val DECELERATE = DecelerateInterpolator()
val ACC_DECELERATE = AccelerateDecelerateInterpolator()

fun View.slideViewOffscreen(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(ACCELERATE)
            .setDuration(400)
            .translationY(height.toFloat())
}

fun View.slideViewOnscreen(): ViewPropertyAnimator {
    visibility = View.VISIBLE

    return animate()
            .withLayer()
            .setInterpolator(DECELERATE)
            .setDuration(400)
            .translationY(0.0f)
}

fun View.slideViewToProperLocation(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(ACC_DECELERATE)
            .setDuration(400)
            .translationY(0.0f)
}
