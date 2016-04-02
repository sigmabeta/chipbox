package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.ObjectAnimator
import android.support.design.widget.FloatingActionButton
import android.transition.TransitionValues
import android.view.View
import net.sigmabeta.chipbox.util.ACCELERATE
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.convertDpToPx
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

    override fun createAnimators(view: View?): List<Animator>? {
        view ?: return null

        val distanceScaler = getDistanceScaler()
        val sizeScale = if (distanceScaler > 0) 1.05f else 0.95f

        val yDelta = distanceScaler * convertDpToPx(64.0f, view.context)

        if (view is FloatingActionButton && distanceScaler > 0) {
            view.fadeInFromBelow().setStartDelay(400)
            return null
        }

        val animations = ArrayList<Animator>(4)

        val fadingAnimation = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f)
        val translAnimation = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, yDelta, 0.0f)
        val xScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_X, sizeScale, 1.0f)
        val yScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_Y, sizeScale, 1.0f)

        fadingAnimation.interpolator = DECELERATE
        translAnimation.interpolator = DECELERATE
        xScaleAnimation.interpolator = ACCELERATE
        yScaleAnimation.interpolator = ACCELERATE

        animations.add(fadingAnimation)
        animations.add(translAnimation)
        animations.add(xScaleAnimation)
        animations.add(yScaleAnimation)

        return animations
    }
}
