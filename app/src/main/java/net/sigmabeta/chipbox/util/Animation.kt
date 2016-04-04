package net.sigmabeta.chipbox.util

import android.R
import android.app.Activity
import android.support.v7.widget.RecyclerView
import android.util.Pair
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.Window
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

val TRANSITION_FADE_OUT_DOWN = FadeOutDownTransition(false, false)
val TRANSITION_FADE_IN_BELOW = FadeInFromBelowTransition(false, false)
val TRANSITION_STAGGERED_FADE_OUT_UP = FadeOutUpTransition(true, false)
val TRANSITION_STAGGERED_FADE_IN_ABOVE = FadeInFromAboveTransition(true, false)

val TRANSITION_FRAGMENT_FADE_OUT_DOWN = FadeOutDownTransition(false, true)
val TRANSITION_FRAGMENT_FADE_IN_BELOW = FadeInFromBelowTransition(false, true)
val TRANSITION_FRAGMENT_STAGGERED_FADE_OUT_UP = FadeOutUpTransition(true, true)
val TRANSITION_FRAGMENT_STAGGERED_FADE_IN_ABOVE = FadeInFromAboveTransition(true, true)

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

fun View.shrinktoNothing() = animate()
        .withLayer()
        .setStartDelay(0)
        .setInterpolator(ACCELERATE)
        .setDuration(200)
        .scaleX(0.0f)
        .scaleY(0.0f)

fun View.growFromNothing() = animate()
        .withLayer()
        .setDuration(75)
        .setInterpolator(DECELERATE)
        .scaleX(1.0f)
        .scaleY(1.0f)

/**
 * Not really anywhere better to put this, I guess.
 */
fun Activity.getShareableNavBar(): Pair<View, String>? {
    return Pair(window.decorView.findViewById(R.id.navigationBarBackground) ?: return null, Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)
}
