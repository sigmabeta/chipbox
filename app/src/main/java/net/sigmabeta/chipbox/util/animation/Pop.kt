package net.sigmabeta.chipbox.util.animation

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.R

/**
 * A transition that animates the alpha, scale X & Y of a view simultaneously.
 */
class Pop(context: Context, attrs: AttributeSet) : Transition(context, attrs) {

    private var enter: Boolean

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.Transitions)
        enter = a.getBoolean(R.styleable.Transitions_enter, true)
        a.recycle()
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        transitionValues.values["whatever"] = true
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        transitionValues.values["whatever"] = false
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator {
        val view = endValues.view

        return if (enter) {
            view.scaleX = 0f
            view.scaleY = 0f
            ObjectAnimator.ofPropertyValuesHolder(
                    view,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1f))
        } else {
            ObjectAnimator.ofPropertyValuesHolder(
                    view,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0f))
        }
    }
}
