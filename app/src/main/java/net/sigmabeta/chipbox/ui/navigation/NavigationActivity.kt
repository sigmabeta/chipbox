package net.sigmabeta.chipbox.ui.navigation

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.layout_now_playing.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.song.SongListFragment
import net.sigmabeta.chipbox.util.loadImageLowQuality
import net.sigmabeta.chipbox.util.slideViewOffscreen
import net.sigmabeta.chipbox.util.slideViewOnscreen
import net.sigmabeta.chipbox.util.slideViewToProperLocation
import javax.inject.Inject

class NavigationActivity : BaseActivity(), NavigationView, FragmentContainer {
    lateinit var presenter: NavigationPresenter
        @Inject set

    /**
     * NavigationView
     */

    override fun showFragment(fragmentTag: String, fragmentArg: Long) {
        var fragment: BaseFragment

        when (fragmentTag) {
            GameGridFragment.FRAGMENT_TAG -> fragment = GameGridFragment.newInstance(fragmentArg)
            SongListFragment.FRAGMENT_TAG -> fragment = SongListFragment.newInstance(fragmentArg)
            else -> {
                showToastMessage("Unsupported fragment.")
                return
            }
        }
        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, fragmentTag)
                .commit()
    }

    override fun setTrackTitle(title: String) {
        text_playing_song_title.text = title
    }

    override fun setArtist(artist: String) {
        text_playing_song_artist.text = artist
    }

    override fun setGameBoxArt(imagePath: String?, fade: Boolean) {
        if (imagePath != null) {
            image_playing_game_box_art.loadImageLowQuality(imagePath, fade, false)
        } else {
            image_playing_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, false)
        }
    }

    override fun showPauseButton() {
        fab_play_pause.setImageResource(R.drawable.ic_pause_white_48dp)
    }

    override fun showPlayButton() {
        fab_play_pause.setImageResource(R.drawable.ic_play_48dp)
    }

    override fun showNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_now_playing).toInt())

        if (animate) {
            layout_now_playing.slideViewOnscreen()
        }
    }

    override fun hideNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, 0)

        if (animate) {
            if (getFragment()?.isScrolledToBottom() ?: false) {
                frame_fragment.translationY = -(resources.getDimension(R.dimen.height_now_playing))
                frame_fragment.slideViewToProperLocation()
            }

            layout_now_playing.slideViewOffscreen().withEndAction {
                layout_now_playing.visibility = View.GONE
            }
        } else {
            layout_now_playing.visibility = View.GONE
        }
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(this, image_playing_game_box_art)
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
        layout_now_playing.setOnClickListener { presenter.onNowPlayingClicked() }
        fab_play_pause.setOnClickListener { presenter.onPlayFabClicked() }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_navigation
    }

    override fun getContentLayout(): FrameLayout {
        return frame_fragment
    }

    override fun setTitle(title: String) {
        this.title = title
    }

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    private fun getFragment(): NavigationFragment? {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_fragment) as NavigationFragment
        return fragment
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.navigation"

        val ARGUMENT_FRAGMENT_TAG = "${ACTIVITY_TAG}.fragment_tag"
        val ARGUMENT_FRAGMENT_ARG = "${ACTIVITY_TAG}.fragment_argument"

        fun launch(context: Context, fragmentTag: String, fragmentArg: Long) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)
            launcher.putExtra(ARGUMENT_FRAGMENT_ARG, fragmentArg)

            context.startActivity(launcher)
        }
    }
}