package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.BuildConfig

abstract class NonSharedTransition : Transition() {

    open fun getAnimationDuration() = 300L

    override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        val view = startValues?.view
        val height = startValues?.values?.get(VALUE_HEIGHT) as Float

        val animations = createAnimators(view, height)

        if (animations?.isEmpty() ?: true) {
            return null
        }

        val set = AnimatorSet()
        set.duration = getAnimationDuration()

        view?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view?.translationY = 0.0f
                view?.scaleX = 1.0f
                view?.scaleY = 1.0f
                view?.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        })

        set.playTogether(animations)
        return set
    }

    protected fun captureHeightAndVisibility(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        val height = view?.height?.toFloat()
        val visibility = view?.visibility

        transitionValues?.values?.put(VALUE_HEIGHT, height)
        transitionValues?.values?.put(VALUE_VISIBILITY, visibility)
    }

    abstract fun createAnimators(view: View?, height: Float): List<Animator>?

    abstract fun getDistanceScaler(): Int

    companion object {
        val VALUE_HEIGHT = "${BuildConfig.APPLICATION_ID}.transition.height"
        val VALUE_VISIBILITY = "${BuildConfig.APPLICATION_ID}.transition.visibility"
    }
}