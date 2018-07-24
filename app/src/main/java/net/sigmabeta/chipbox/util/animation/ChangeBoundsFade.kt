package net.sigmabeta.chipbox.util.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.transition.ChangeBounds
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.R

class ChangeBoundsFade(context: Context, attrs: AttributeSet) : ChangeBounds(context, attrs) {

    private var fadeIn: Boolean

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ChangeBoundsFade)
        fadeIn = a.getBoolean(R.styleable.ChangeBoundsFade_fadeIn, true)
        a.recycle()
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator {
        val view = endValues.view
        val superAnim = super.createAnimator(sceneRoot, startValues, endValues)

        val fadeDuration: Long
        val fadeStartDelay: Long

        val start: Float
        val end: Float

        if (fadeIn) {
            start = 1.0f
            end = 0.0f
            fadeDuration = duration / 4
            fadeStartDelay = duration * 3 / 4
        } else {
            start = 0.0f
            end = 1.0f
            fadeDuration = duration / 5
            fadeStartDelay = 0
//            superAnim.startDelay = 63
        }

        val fadeAnim = ObjectAnimator.ofFloat(view, View.ALPHA, start, end)
        fadeAnim.duration = fadeDuration
        fadeAnim.startDelay = fadeStartDelay

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(superAnim, fadeAnim)

        return animatorSet
    }
}