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
import android.widget.TextView
import net.sigmabeta.chipbox.ui.util.transition.nonshared.*
import timber.log.Timber

val SCROLL_DIRECTION_DOWN = 1
val SCROLL_DIRECTION_UP = -1

fun RecyclerView.isScrolledToBottom(): Boolean {
    val canScrollDown = canScrollVertically(SCROLL_DIRECTION_DOWN)
    val canScrollUp = canScrollVertically(SCROLL_DIRECTION_UP)

    Timber.v("ScrollUp %b ScrollDown %b", canScrollUp, canScrollDown)

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

val TRANSITION_SLIDE = SlideTransition()

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

fun View.fadeIn(): ViewPropertyAnimator {
    visibility = View.VISIBLE

    return animate()
            .withLayer()
            .setInterpolator(DECELERATE)
            .setDuration(150)
            .alpha(1.0f)
}

fun View.fadeInFromZero(): ViewPropertyAnimator {
    visibility = View.VISIBLE
    alpha = 0.0f

    return animate()
            .withLayer()
            .setInterpolator(DECELERATE)
            .setDuration(150)
            .alpha(1.0f)
}

fun View.fadeOutGone(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(ACCELERATE)
            .setDuration(150)
            .alpha(0.0f)
            .withEndAction {
                visibility = View.GONE
            }
}

fun View.fadeOut(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(ACCELERATE)
            .setDuration(150)
            .alpha(0.0f)
}

fun View.fadeOutPartially(): ViewPropertyAnimator {
    return animate()
            .withLayer()
            .setInterpolator(ACCELERATE)
            .setDuration(150)
            .alpha(0.6f)
}

fun TextView.changeText(text: String) = if (getText() != text) {
    animate().withLayer()
            .setDuration(50)
            .setInterpolator(DECELERATE)
            .alpha(0.0f)
            .withEndAction {
                setText(text)

                animate().withLayer()
                        .setDuration(100)
                        .setInterpolator(DECELERATE)
                        .alpha(1.0f)
            }
} else {
    null
}


fun View.shrinktoNothing() = animate()
        .withLayer()
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
