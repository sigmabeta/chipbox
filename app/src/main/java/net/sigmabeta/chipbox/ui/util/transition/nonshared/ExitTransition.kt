package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.ACCELERATE
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

    override fun createAnimators(view: View?, height: Float): List<Animator>? {
        val animations = ArrayList<Animator>(2)
        val distanceScaler = getDistanceScaler()

        animations.add(ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f))
        animations.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0.0f, distanceScaler * height))

        val sizeScale = if (distanceScaler > 0) {
            1.1f
        } else 0.9f

        animations.add(ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, sizeScale))
        animations.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, sizeScale))

        return animations
    }

    override fun getAnimationInterpolator() = ACCELERATE
}
