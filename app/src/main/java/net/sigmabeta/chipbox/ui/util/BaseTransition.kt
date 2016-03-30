package net.sigmabeta.chipbox.ui.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import net.sigmabeta.chipbox.BuildConfig

abstract class BaseTransition : Transition() {

    open fun getAnimationDuration() = 300L

    override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        val view = startValues?.view
        val height = startValues?.values?.get(VALUE_HEIGHT) as Float

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

        return fillAnimatorSet(set, view, height)
    }

    protected fun captureHeightAndVisibility(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val height = view?.height?.toFloat()
        val visibility = view?.visibility

        transitionValues?.values?.put(VALUE_HEIGHT, height)
        transitionValues?.values?.put(VALUE_VISIBILITY, visibility)
    }

    abstract fun fillAnimatorSet(set: AnimatorSet, view: View?, height: Float): AnimatorSet?

    abstract fun getDistanceScaler(): Int

    abstract fun getAnimationInterpolator(): Interpolator

    companion object {
        val VALUE_HEIGHT = "${BuildConfig.APPLICATION_ID}.transition.height"
        val VALUE_VISIBILITY = "${BuildConfig.APPLICATION_ID}.transition.visibility"
    }
}