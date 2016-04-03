package net.sigmabeta.chipbox.util

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import net.sigmabeta.chipbox.ui.util.transition.nonshared.FadeInFromAboveTransition
import net.sigmabeta.chipbox.ui.util.transition.nonshared.FadeInFromBelowTransition
import net.sigmabeta.chipbox.ui.util.transition.nonshared.FadeOutDownTransition
import net.sigmabeta.chipbox.ui.util.transition.nonshared.FadeOutUpTransition

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

val TRANSITION_FADE_OUT_UP = FadeOutUpTransition()
val TRANSITION_FADE_OUT_DOWN = FadeOutDownTransition()
val TRANSITION_FADE_IN_ABOVE = FadeInFromAboveTransition()
val TRANSITION_FADE_IN_BELOW = FadeInFromBelowTransition()

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

fun View.fadeOutToLeft(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(DECELERATE)
            .setDuration(200)
            .scaleY(0.8f)
            .scaleX(1.1f)
            .translationX(-width / 4.0f)
            .alpha(0.0f)
}

fun View.fadeInFromRight(): ViewPropertyAnimator {
    translationX = width / 4.0f
    alpha = 0.0f
    visibility = View.VISIBLE

    return animate()
            .withLayer()
            .setInterpolator(ACCELERATE)
            .setDuration(300)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationX(0.0f)
            .alpha(1.0f)
}

fun View.fadeInFromBelow(): ViewPropertyAnimator {
    alpha = 0.0f
    visibility = View.VISIBLE
    translationY = height.toFloat()

    return animate()
            .withLayer()
            .setInterpolator(DECELERATE)
            .setDuration(300)
            .translationY(0.0f)
            .alpha(1.0f)
}
