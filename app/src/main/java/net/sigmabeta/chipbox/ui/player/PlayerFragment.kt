package net.sigmabeta.chipbox.ui.player

import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import kotlinx.android.synthetic.main.fragment_player.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.util.changeText
import net.sigmabeta.chipbox.util.loadImageHighQuality
import timber.log.Timber
import javax.inject.Inject

class PlayerFragment : BaseFragment<PlayerFragmentPresenter, PlayerFragmentView>(), PlayerFragmentView, SeekBar.OnSeekBarChangeListener {
    lateinit var presenter: PlayerFragmentPresenter
        @Inject set


    /**
     * PlayerFragmentView
     */

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (isResumed) {
            if (animate) {
                text_track_title.changeText(title)
            } else {
                text_track_title.text = title
            }
        }
    }

    override fun setGameTitle(title: String, animate: Boolean) {
        if (isResumed) {
            if (animate) {
                text_game_title.changeText(title)
            } else {
                text_game_title.text = title
            }
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (isResumed) {
            if (animate) {
                text_track_artist.changeText(artist)
            } else {
                text_track_artist.text = artist
            }
        }
    }

    override fun setTimeElapsed(time: String) {
        if (isResumed) {
            text_track_elapsed.text = time
        }
    }

    override fun setTrackLength(trackLength: String, animate: Boolean) {
        if (isResumed) {
            if (animate) {
                text_track_length.changeText(trackLength)
            } else {
                text_track_length.text = trackLength
            }
        }
    }

    override fun setGameBoxArt(path: String?, fade: Boolean) {
        if (isResumed) {
             TODO("Replace null with Aspect ratio")
            Handler().postDelayed({
            if (path != null) {
                image_game_box_art.loadImageHighQuality(path, fade, 1.0f, getPicassoCallback())
            } else {
                image_game_box_art.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, null, getPicassoCallback())
            }}, 200)
        }
    }

    override fun setUnderrunCount(count: String) {
        if (isResumed) {
            text_underrun_count.text = count
        }
    }

    override fun setProgress(percentPlayed: Int) {
        if (isResumed) {
            seek_playback_progress.progress = percentPlayed
        }
    }

    override fun showPlaylist() {
        (activity as PlayerActivityView).onPlaylistFabClicked()
    }

    /**
     * OnSeekbarChangeListener
     */

    override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
        presenter.onSeekbarChanged(progress)
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {
        presenter.onSeekbarTouch()
    }

    override fun onStopTrackingTouch(bar: SeekBar?) {
        presenter.onSeekbarRelease(bar?.progress ?: 0)
    }

    /**
     * BaseFragment
     */

    override fun inject(): Boolean {
        val container = activity
        if (container is BaseActivity<*, *>) {container.getFragmentComponent()?.let {


                it.inject(this)
                return true
            } ?: let {
                Timber.e("${className()} injection failure: ${container?.className()}'s FragmentComponent not valid.")
                return false
            }
        } else {
            Timber.e("${className()} injection failure: ${container?.className()} not valid.")
            return false
        }
    }

    override fun showLoadingState() = Unit

    override fun showContent() = Unit
    override fun getContentLayout(): ViewGroup {
        return frame_content
    }

    override fun getPresenterImpl() = presenter

    override fun getLayoutId(): Int {
        return R.layout.fragment_player
    }

    override fun getSharedImage(): View? {
        return image_game_box_art
    }

    override fun configureViews() {
        button_fab.setOnClickListener {
            presenter.onFabClick()
        }

        seek_playback_progress.setOnSeekBarChangeListener(this)
    }

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.player"

        fun newInstance(): PlayerFragment {
            val fragment = PlayerFragment()

            return fragment
        }
    }
}
