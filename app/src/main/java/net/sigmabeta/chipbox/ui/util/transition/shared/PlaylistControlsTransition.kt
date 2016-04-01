package net.sigmabeta.chipbox.ui.util.transition.shared

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.util.DECELERATE

class PlaylistControlsTransition : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues?) {
        captureHelper(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues?) {
        captureHelper(transitionValues)
    }

    protected fun captureHelper(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val elevation = view?.elevation
        val location = IntArray(2)
        view?.getLocationInWindow(location)

        transitionValues?.values?.put(VALUE_ELEVATION, elevation)
        transitionValues?.values?.put(VALUE_POSITION_Y, location[1])
    }

    override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        val view = startValues?.view

        val startY = startValues?.values?.get(VALUE_POSITION_Y) as Int
        val endY = endValues?.values?.get(VALUE_POSITION_Y) as Int

        val startElevation = startValues?.values?.get(VALUE_ELEVATION) as Float
        val endElevation = endValues?.values?.get(VALUE_ELEVATION) as Float

        val yDelta = endY - startY

        val set = AnimatorSet()
        set.duration = getAnimationDuration()
        set.interpolator = getAnimationInterpolator()

        view?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view?.translationY = 0.0f
                view?.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        })

        return fillAnimatorSet(set, view, yDelta, startElevation, endElevation)
    }

    fun fillAnimatorSet(set: AnimatorSet, view: View?, yDelta: Int, startElevation: Float, endElevation: Float): AnimatorSet? {

        set.playTogether(
                ObjectAnimator.ofFloat(view, "elevation", startElevation, endElevation),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -yDelta.toFloat(), 0.0f)
        )

        return set
    }

    fun getAnimationDuration() = 200L
    fun getAnimationInterpolator() = DECELERATE

    companion object {
        val VALUE_ELEVATION = "${BuildConfig.APPLICATION_ID}.transition.elevation"
        val VALUE_POSITION_Y = "${BuildConfig.APPLICATION_ID}.transition.position_y"
    }
}