package net.sigmabeta.chipbox.ui.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.support.design.widget.FloatingActionButton
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.fadeInFromBelow

abstract class EnterTransition : BaseTransition() {
    override fun captureEndValues(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val height = view?.height?.toFloat()

        transitionValues?.values?.put(VALUE_HEIGHT, height)
        transitionValues?.values?.put(VALUE_VISIBILITY, View.GONE)
    }

    override fun captureStartValues(transitionValues: TransitionValues?) {
        captureHeightAndVisibility(transitionValues)
    }

    override fun fillAnimatorSet(set: AnimatorSet, view: View?, height: Float): AnimatorSet? {
        if (view is FloatingActionButton) {
            view.fadeInFromBelow().setStartDelay(400)
            return null
        }

        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, getDistanceScaler() * height, 0.0f)
        )

        return set
    }

    override fun getAnimationInterpolator() = DECELERATE
}
