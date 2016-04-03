package net.sigmabeta.chipbox.ui.player

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_player.*
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.playlist.PlaylistFragment
import javax.inject.Inject

class PlayerActivity : BaseActivity(), PlayerActivityView, FragmentContainer {
    lateinit var presenter: PlayerActivityPresenter
        @Inject set

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

    /**
     * FragmentContainer
     */

    override fun setTitle(title: String) {
        setTitle(title)
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

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

    companion object {
        fun launch(activity: Activity, sharedView: View) {
            val launcher = Intent(activity, PlayerActivity::class.java)

            val options = ActivityOptions.makeSceneTransitionAnimation(activity, sharedView, "image_playing_boxart")

            activity.startActivity(launcher, options.toBundle())
        }

        fun getLauncher(context: Context): Intent {
            return Intent(context, PlayerActivity::class.java)
        }
    }
}