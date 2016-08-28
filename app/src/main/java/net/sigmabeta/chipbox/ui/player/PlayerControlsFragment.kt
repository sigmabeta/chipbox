package net.sigmabeta.chipbox.ui.player

import android.animation.*
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.ImageView
import kotlinx.android.synthetic.main.fragment_player_controls.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.ACC_DECELERATE
import net.sigmabeta.chipbox.util.convertDpToPx
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PlayerControlsFragment : BaseFragment(), PlayerControlsView {
    lateinit var presenter: PlayerControlsPresenter
        @Inject set

    override fun finish() {
        activity.finish()
    }

    /**
     * PlayerControlsView
     */

    override fun showPauseButton() {
        button_play.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play.setImageResource(R.drawable.ic_play_arrow_black_24dp)
    }

    override fun elevate() {
        animateControls(frame_content, true)
    }

    override fun unElevate() {
        animateControls(frame_content, false)
    }

    override fun onPlaylistShown() {
        presenter.onPlaylistShown()
    }

    override fun onPlaylistHidden() {
        presenter.onPlaylistHidden()
    }

    override fun setShuffleEnabled() {
        setViewTint(button_shuffle, R.color.accent)
    }

    override fun setShuffleDisabled() {
        setViewTint(button_shuffle, R.color.circle_grey)
    }

    override fun setRepeatDisabled() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.circle_grey)
    }

    override fun setRepeatAll() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.accent)
    }

    override fun setRepeatOne() {
        button_repeat.setImageResource(R.drawable.ic_repeat_one_black_24dp)
        setViewTint(button_repeat, R.color.accent)
    }

    override fun setRepeatInfinite() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.primary)
    }

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

    override fun getPresenter(): FragmentPresenter = presenter

    override fun getLayoutId() = R.layout.fragment_player_controls

    override fun getContentLayout() = frame_content

    override fun getSharedImage() = null

    override fun configureViews() {
        button_play.setOnClickListener(this)
        button_skip_back.setOnClickListener(this)
        button_skip_forward.setOnClickListener(this)
        button_shuffle.setOnClickListener(this)
        button_repeat.setOnClickListener(this)
    }

    /**
     * Private Methods
     */

    private fun setViewTint(view: ImageView, colorId: Int) {
        val color = ContextCompat.getColor(activity, colorId)
        view.drawable.setTint(color)
    }

    private fun animateControls(view: View, elevate: Boolean) {
        val set = AnimatorSet()

        set.duration = 300L
        set.interpolator = ACC_DECELERATE

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                view.translationY = 0.0f
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                if (!elevate) {
                    view.setBackgroundColor(ContextCompat.getColor(view.context, android.R.color.transparent))
                }
            }
        })

        val animations = ArrayList<Animator>(2)

        val startElevation = if (elevate) 0.0f else convertDpToPx(16.0f, activity)
        val endElevation = if (elevate) convertDpToPx(16.0f, activity) else 0.0f

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

        set.playTogether(animations)
        set.start()
    }

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.player_controls"

        fun newInstance(): PlayerControlsFragment {
            val fragment = PlayerControlsFragment()
            return fragment
        }
    }
}