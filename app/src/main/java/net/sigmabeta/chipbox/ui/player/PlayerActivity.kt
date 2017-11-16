package net.sigmabeta.chipbox.ui.player

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat
import android.util.Pair
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_player.*
import kotlinx.android.synthetic.main.fragment_player.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.playlist.PlaylistFragment
import net.sigmabeta.chipbox.util.ACC_DECELERATE
import net.sigmabeta.chipbox.util.shrinktoNothing
import javax.inject.Inject

class PlayerActivity : BaseActivity<PlayerActivityPresenter, PlayerActivityView>(), PlayerActivityView, FragmentContainer {
    lateinit var presenter: PlayerActivityPresenter
        @Inject set

    var alreadyFinishing = false

    /**
     * PlayerView
     */

    override fun onPlaylistFabClicked() {
        presenter.onClick(R.id.button_fab)
    }

    override fun showPlayerFragment() {
        var fragment = PlayerFragment.newInstance()

        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, PlayerFragment.FRAGMENT_TAG)
                .commit()
    }

    override fun showControlsFragment() {
        var fragment = PlayerControlsFragment.newInstance()

        supportFragmentManager.beginTransaction()
                .add(R.id.frame_controls, fragment, PlayerControlsFragment.FRAGMENT_TAG)
                .commit()
    }

    override fun showPlaylistFragment() {
        var fragment = PlaylistFragment.newInstance()

        supportFragmentManager.beginTransaction()
                .addToBackStack(null)
                .replace(R.id.frame_fragment, fragment, PlaylistFragment.FRAGMENT_TAG)
                .commit()

        val controls = getControlsFragment()
        controls?.onPlaylistShown()
    }

    override fun hidePlaylistFragment() {
        supportFragmentManager.popBackStackImmediate()

        val controls = getControlsFragment()
        controls?.onPlaylistHidden()
    }

    override fun callFinish() {
        if (!alreadyFinishing) {
            alreadyFinishing = true
            button_fab.shrinktoNothing().withEndAction {
                supportFinishAfterTransition()
            }
        }
    }

    override fun showStatusBar() {
        frame_fragment.fitsSystemWindows = true
        animateStatusBar(ContextCompat.getColor(this, R.color.grey_translucent),
                ContextCompat.getColor(this, R.color.primary_dark))
    }

    override fun hideStatusBar() {
        frame_fragment.fitsSystemWindows = false
        animateStatusBar(ContextCompat.getColor(this, R.color.primary_dark),
                ContextCompat.getColor(this, R.color.grey_translucent))
    }

    /**
     * FragmentContainer
     */

    override fun setTitle(title: String) {
        setTitle(title)
    }

    /**
     * BaseActivity
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject() {
        getTypedApplication().appComponent.inject(this)
    }

    override fun getPresenterImpl() = presenter

    override fun configureViews() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_player
    }

    override fun getContentLayout(): FrameLayout {
        return frame_fragment
    }

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = true

    /**
     * Activity
     */

    override fun onBackPressed() {
        presenter.onClick(R.string.back_button)
    }

    /**
     * Private Methods
     */

    private fun getControlsFragment(): PlayerControlsView? {
        return (supportFragmentManager.findFragmentByTag(PlayerControlsFragment.FRAGMENT_TAG)) as PlayerControlsView
    }

    private fun animateStatusBar(fromColor: Int, toColor: Int) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)

        colorAnimation.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            window.statusBarColor = color
        }

        colorAnimation.duration = 300L
        colorAnimation.interpolator = ACC_DECELERATE

        colorAnimation.start()
    }

    companion object {
        fun launch(activity: Activity, sharedViewPairs: Array<Pair<View, String>>?) {
            val launcher = Intent(activity, PlayerActivity::class.java)

            val options = if (sharedViewPairs != null) {
                ActivityOptions.makeSceneTransitionAnimation(activity, *sharedViewPairs)
            } else {
                ActivityOptions.makeSceneTransitionAnimation(activity)
            }

            activity.startActivity(launcher, options.toBundle())
        }

        fun getLauncher(context: Context): Intent {
            return Intent(context, PlayerActivity::class.java)
        }
    }
}