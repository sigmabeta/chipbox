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
import net.sigmabeta.chipbox.util.ACCELERATE
import net.sigmabeta.chipbox.util.DECELERATE
import net.sigmabeta.chipbox.util.convertDpToPx
import net.sigmabeta.chipbox.util.fadeInFromBelow
import java.util.*

abstract class NonSharedTransition : Visibility() {

    override fun onAppear(sceneRoot: ViewGroup?, view: View?, startValues: TransitionValues?, endValues: TransitionValues?) = view?.let { createAnimatorSet(it, true) } ?: null

    override fun onDisappear(sceneRoot: ViewGroup?, view: View?, startValues: TransitionValues?, endValues: TransitionValues?) = view?.let { createAnimatorSet(it, false) } ?: null

    fun createAnimatorSet(view: View, appear: Boolean): Animator? {
        val animations = createAnimators(view, appear) ?: return null

        val set = AnimatorSet()
        set.duration = DURATION

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view.translationY = 0.0f
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        })

        set.playTogether(animations)
        return set
    }

    fun createAnimators(view: View, appear: Boolean): List<Animator>? {
        val distanceScaler = getDistanceScaler()

        if (view is FloatingActionButton && distanceScaler > 0 && appear) {
            view.fadeInFromBelow().setStartDelay(400)
            return null
        }

        val animations = ArrayList<Animator>(4)

        val fadingAnimation = ObjectAnimator.ofFloat(view, View.ALPHA, getStartAlpha(appear), getEndAlpha(appear))
        val translAnimation = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, getStartY(view, appear), getEndY(view, appear))
        val xScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_X, getStartScale(appear), getEndScale(appear))
        val yScaleAnimation = ObjectAnimator.ofFloat(view, View.SCALE_Y, getStartScale(appear), getEndScale(appear))

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

    fun getStartY(view: View, appear: Boolean) = if (appear) getDistanceScaler() * convertDpToPx(64.0f, view.context) else TRANSLATION_DEFAULT

    fun getEndY(view: View, appear: Boolean) = if (appear) TRANSLATION_DEFAULT else getDistanceScaler() * convertDpToPx(64.0f, view.context)

    fun getStartScale(appear: Boolean) = if (appear) getScaleFactor() else SCALE_DEFAULT

    fun getEndScale(appear: Boolean) = if (appear) SCALE_DEFAULT else getScaleFactor()

    fun getStartAlpha(appear: Boolean) = if (appear) ALPHA_TRANSPARENT else ALPHA_OPAQUE

    fun getEndAlpha(appear: Boolean) = if (appear) ALPHA_OPAQUE else ALPHA_TRANSPARENT

    fun getScaleFactor() = if (getDistanceScaler() > 0) SCALE_LARGE else SCALE_SMALL

    abstract fun getDistanceScaler(): Int

    companion object {
        val DURATION = 300L

        val TRANSLATION_DEFAULT = 0.0f

        val ALPHA_TRANSPARENT = 0.0f
        val ALPHA_OPAQUE = 1.0f

        val SCALE_DEFAULT = 1.0f
        val SCALE_LARGE = 1.05f
        val SCALE_SMALL = 0.95f
    }
}