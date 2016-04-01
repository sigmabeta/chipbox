package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.ObjectAnimator
import android.support.design.widget.FloatingActionButton
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.fadeInFromBelow
import java.util.*

abstract class EnterTransition : NonSharedTransition() {
    override fun captureEndValues(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val height = view?.height?.toFloat()

        transitionValues?.values?.put(VALUE_HEIGHT, height)
        transitionValues?.values?.put(VALUE_VISIBILITY, View.GONE)
    }

    override fun captureStartValues(transitionValues: TransitionValues?) {
        captureHeightAndVisibility(transitionValues)
    }

    override fun createAnimators(view: View?, height: Float): List<Animator>? {
        val distanceScaler = getDistanceScaler()

        if (view is FloatingActionButton && distanceScaler > 0) {
            view.fadeInFromBelow().setStartDelay(400)
            return null
        }

        val animations = ArrayList<Animator>(2)

        animations.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f))
        animations.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, distanceScaler * height, 0.0f))

        return animations
    }

    override fun getAnimationInterpolator() = DECELERATE
}
