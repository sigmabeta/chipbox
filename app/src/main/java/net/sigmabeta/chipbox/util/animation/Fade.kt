package net.sigmabeta.chipbox.util.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.R

class Fade(context: Context, attrs: AttributeSet) : Transition(context, attrs) {

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

        val startAlpha: Float
        val endAlpha: Float

        if (!enter) {
            startAlpha = 1.0f
            endAlpha = 0.0f
        } else {
            view.alpha = 0.0f

            startAlpha = 0.0f
            endAlpha = 1.0f
        }

        val fadeAnim = ObjectAnimator.ofFloat(view, View.ALPHA, startAlpha, endAlpha)

        fadeAnim.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) = Unit
            override fun onAnimationCancel(animation: Animator?)  = Unit
            override fun onAnimationStart(animation: Animator?) = Unit

            override fun onAnimationEnd(animation: Animator?) {
                if (!enter) {
                    view.translationY = view.height.toFloat() * 10.0f
                }
            }
        })

        return fadeAnim
    }
}