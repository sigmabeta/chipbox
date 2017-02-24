package net.sigmabeta.chipbox.ui.navigation

import android.content.Context
import android.content.Intent
import android.util.Pair
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.layout_status.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.NavigationFragment
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.track.TrackListFragment
import net.sigmabeta.chipbox.util.*
import java.util.*
import javax.inject.Inject

class NavigationActivity : BaseActivity<NavigationPresenter, NavigationView>(), NavigationView, FragmentContainer {
    lateinit var presenter: NavigationPresenter
        @Inject set

    /**
     * NavigationView
     */

    override fun showFragment(fragmentTag: String, fragmentArg: String?) {
        var fragment: BaseFragment<*, *>

        when (fragmentTag) {
            GameGridFragment.FRAGMENT_TAG -> fragment = GameGridFragment.newInstance(fragmentArg)
            TrackListFragment.FRAGMENT_TAG -> fragment = TrackListFragment.newInstance(fragmentArg)
            else -> {
                showToastMessage("Unsupported fragment.")
                return
            }
        }
        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, fragmentTag)
                .commit()
    }

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (layout_now_playing.translationY == 0.0f && animate) {
            text_playing_song_title.changeText(title)
        } else {
            text_playing_song_title.text = title
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (layout_now_playing.translationY == 0.0f && animate) {
            text_playing_song_artist.changeText(artist)
        } else {
            text_playing_song_artist.text = artist
        }
    }

    override fun setGameBoxArt(imagePath: String?, fade: Boolean) {
        if (imagePath != null) {
            image_playing_game_box_art.loadImageLowQuality(imagePath, fade, false)
        } else {
            image_playing_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, false)
        }
    }

    override fun showPauseButton() {
        button_play_pause.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play_pause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
    }

    override fun showNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_status_bar).toInt())

        if (animate) {
            layout_now_playing.slideViewOnscreen()
        } else {
            layout_now_playing.translationY = 0.0f
            layout_now_playing.visibility = View.VISIBLE
        }
    }

    override fun hideNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, 0)

        if (animate) {
            if (getFragment()?.isScrolledToBottom() ?: false) {
                frame_fragment.translationY = -(resources.getDimension(R.dimen.height_status_bar))
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
        PlayerActivity.launch(this, getShareableViews())
    }

    /**
     * BaseActivity
     */

    override fun getShareableViews(): Array<Pair<View, String>>? {
        val views = ArrayList<Pair<View, String>>(3)

        views.add(Pair(image_playing_game_box_art as View, "image_playing_boxart"))
        views.add(Pair(button_play_pause as View, "button_play_pause"))

        getShareableNavBar()?.let {
            views.add(it)
        }

        return views.toTypedArray()
    }

    override fun showLoading() = Unit

    override fun hideLoading() = Unit

    override fun inject() {
        getTypedApplication().appComponent.inject(this)
    }

    override fun getPresenterImpl(): NavigationPresenter {
        return presenter
    }

    override fun configureViews() {
        layout_now_playing.setOnClickListener { presenter.onNowPlayingClicked() }
        button_play_pause.setOnClickListener { presenter.onPlayFabClicked() }
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


    /**
     * Private Methods
     */

    private fun getFragment(): NavigationFragment? {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_fragment) as NavigationFragment
        return fragment
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.navigation"

        val ARGUMENT_FRAGMENT_TAG = "${ACTIVITY_TAG}.fragment_tag"
        val ARGUMENT_FRAGMENT_ARG_STRING = "${ACTIVITY_TAG}.fragment_argument_string"

        fun launch(context: Context, fragmentTag: String, fragmentArg: String) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)
            launcher.putExtra(ARGUMENT_FRAGMENT_ARG_STRING, fragmentArg)

            context.startActivity(launcher)
        }

        fun launch(context: Context, fragmentTag: String) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)

            context.startActivity(launcher)
        }
    }
}