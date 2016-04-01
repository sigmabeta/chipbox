package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.ACCELERATE

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

    override fun fillAnimatorSet(set: AnimatorSet, view: View?, height: Float): AnimatorSet? {

        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0.0f, getDistanceScaler() * height)
        )

        return set
    }

    override fun getAnimationInterpolator() = ACCELERATE
}
