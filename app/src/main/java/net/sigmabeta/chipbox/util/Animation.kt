package net.sigmabeta.chipbox.util

import android.support.v7.widget.RecyclerView
import android.util.Pair
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import timber.log.Timber
import java.util.*

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


fun removeNullViewPairs(vararg views: Pair<View, String>?): Array<Pair<View, String>> {
    val viewsList = ArrayList<Pair<View, String>>(views.size)

    views.forEach {
        if (it != null) {
            viewsList.add(it)
        }
    }

    return viewsList.toTypedArray()
}
