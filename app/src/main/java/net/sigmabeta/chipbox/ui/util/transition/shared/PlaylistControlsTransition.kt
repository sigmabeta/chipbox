package net.sigmabeta.chipbox.ui.util.transition.shared

import android.animation.*
import android.support.v4.content.ContextCompat
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.util.DECELERATE
import java.util.*

class PlaylistControlsTransition : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues?) {
        captureHelper(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues?) {
        captureHelper(transitionValues)
    }

    protected fun captureHelper(transitionValues: TransitionValues?) {
        val view = transitionValues?.view

        if (view !is FrameLayout) {
            return
        }

        val elevation = view.elevation
        val location = IntArray(2)
        view.getLocationInWindow(location)

        transitionValues?.values?.put(VALUE_ELEVATION, elevation)
        transitionValues?.values?.put(VALUE_POSITION_Y, location[1])
    }

    override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        val view = startValues?.view

        if (view !is FrameLayout) {
            return null
        }

        val startY = startValues?.values?.get(VALUE_POSITION_Y) as Int
        val endY = endValues?.values?.get(VALUE_POSITION_Y) as Int

        val startElevation = startValues?.values?.get(VALUE_ELEVATION) as Float
        val endElevation = endValues?.values?.get(VALUE_ELEVATION) as Float

        val yDelta = endY - startY

        val animations = createAnimators(view, yDelta, startElevation, endElevation)

        if (animations?.isEmpty() ?: true) {
            return null
        }

        val set = AnimatorSet()

        set.duration = getAnimationDuration()
        set.interpolator = getAnimationInterpolator()

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view.translationY = 0.0f
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        })

        set.playTogether(animations)

        return set
    }

    fun createAnimators(view: View, yDelta: Int, startElevation: Float, endElevation: Float): List<Animator>? {
        val animations = ArrayList<Animator>(3)

        if (yDelta != 0) {
            animations.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -yDelta.toFloat(), 0.0f))
        }

        if (startElevation != endElevation) {
            animations.add(ObjectAnimator.ofFloat(view, "elevation", startElevation, endElevation))

            val colorAnimation = if (endElevation > startElevation) {
                val startColor = ContextCompat.getColor(view.context, R.color.background_grey)
                val endcolor = ContextCompat.getColor(view.context, android.R.color.white)
                ValueAnimator.ofObject(ArgbEvaluator(), startColor, endcolor)
            } else {
                val startColor = ContextCompat.getColor(view.context, android.R.color.white)
                val endcolor = ContextCompat.getColor(view.context, R.color.background_grey)
                ValueAnimator.ofObject(ArgbEvaluator(), startColor, endcolor)
            }

            colorAnimation.addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                view.setBackgroundColor(color)
            }

            animations.add(colorAnimation)
        }

        return animations
    }

    fun getAnimationDuration() = 500L
    fun getAnimationInterpolator() = DECELERATE

    companion object {
        val VALUE_ELEVATION = "${BuildConfig.APPLICATION_ID}.transition.elevation"
        val VALUE_POSITION_Y = "${BuildConfig.APPLICATION_ID}.transition.position_y"
    }
}