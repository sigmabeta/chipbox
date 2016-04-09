package net.sigmabeta.chipbox.ui.util.transition.nonshared

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.support.design.widget.FloatingActionButton
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.convertDpToPx
import net.sigmabeta.chipbox.util.growFromNothing
import java.util.*

class SlideTransition() : Visibility() {
    override fun onAppear(sceneRoot: ViewGroup?, view: View?, startValues: TransitionValues?, endValues: TransitionValues?) = view?.let { createAnimatorSet(it, true) } ?: null

    override fun onDisappear(sceneRoot: ViewGroup?, view: View?, startValues: TransitionValues?, endValues: TransitionValues?) = view?.let { createAnimatorSet(it, false) } ?: null

    fun createAnimatorSet(view: View, appear: Boolean): Animator? {
        val animations = createAnimators(view, appear) ?: return null

        val set = AnimatorSet()
        set.duration = DURATION

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                view.translationY = 0.0f
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        })

        set.playTogether(animations)
        return set
    }

    fun createAnimators(view: View, appear: Boolean): List<Animator>? {
        if (view is FloatingActionButton) {
            if (appear) {
                view.growFromNothing().setStartDelay(500).withEndAction { view.animate().setStartDelay(0) }
            }

            return null
        }

        val animations = ArrayList<Animator>(1)

        val translAnimation = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, getStartY(view, appear), getEndY(view, appear))

        translAnimation.interpolator = decelerateIf(appear)
        animations.add(translAnimation)

        return animations
    }

    fun getStartY(view: View, appear: Boolean) = if (appear) convertDpToPx(TRANSLATION_OFFSET, view.context) else TRANSLATION_DEFAULT

    fun getEndY(view: View, appear: Boolean) = if (appear) TRANSLATION_DEFAULT else convertDpToPx(TRANSLATION_OFFSET, view.context)

    private fun decelerateIf(appear: Boolean) = if (appear) DECELERATE else DECELERATE

    companion object {
        val DURATION = 500L
        val DELAY_FRAGMENT_EXIT = 25L

        val TRANSLATION_DEFAULT = 0.0f
        val TRANSLATION_OFFSET = 400.0f
    }
}