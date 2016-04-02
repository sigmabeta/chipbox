package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.ACCELERATE
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.convertDpToPx
import java.util.*

abstract class ExitTransition : NonSharedTransition() {
    override fun captureStartValues(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val height = view?.height?.toFloat()

        transitionValues?.values?.put(VALUE_HEIGHT, height)
        transitionValues?.values?.put(VALUE_VISIBILITY, View.GONE)
    }

    override fun captureEndValues(transitionValues: TransitionValues?) {
        captureHeightAndVisibility(transitionValues)
    }

    override fun createAnimators(view: View?): List<Animator>? {
        view ?: return null

        val animations = ArrayList<Animator>(4)

        val distanceScaler = getDistanceScaler()
        val sizeScale = if (distanceScaler > 0) 1.05f else 0.95f

        val yDelta = distanceScaler * convertDpToPx(64.0f, view.context)

        val fadingAnimation = ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f)
        val translAnimation = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0.0f, yDelta)
        val xScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, sizeScale)
        val yScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, sizeScale)

        fadingAnimation.interpolator = ACCELERATE
        translAnimation.interpolator = ACCELERATE
        xScaleAnimation.interpolator = DECELERATE
        yScaleAnimation.interpolator = DECELERATE

        animations.add(fadingAnimation)
        animations.add(translAnimation)
        animations.add(xScaleAnimation)
        animations.add(yScaleAnimation)

        return animations
    }
}
