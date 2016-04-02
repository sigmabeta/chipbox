package net.sigmabeta.chipbox.ui.player

import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import kotlinx.android.synthetic.main.fragment_player.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.loadImageHighQuality
import javax.inject.Inject

class PlayerFragment : BaseFragment(), PlayerFragmentView, SeekBar.OnSeekBarChangeListener {
    lateinit var presenter: PlayerFragmentPresenter
        @Inject set

    /**
     * PlayerFragmentView
     */

    override fun setTrackTitle(title: String) {
        text_track_title.text = title
    }

    override fun setGameTitle(title: String) {
        text_game_title.text = title
    }

    override fun setArtist(artist: String) {
        text_track_artist.text = artist
    }

    override fun setTimeElapsed(time: String) {
        text_track_elapsed.text = time
    }

    override fun setTrackLength(trackLength: String) {
        text_track_length.text = trackLength
    }

    override fun setGameBoxArt(path: String?, fade: Boolean) {
        if (path != null) {
            image_game_box_art.loadImageHighQuality(path, fade, false, getPicassoCallback())
        } else {
            image_game_box_art.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, false, getPicassoCallback())
        }
    }

    override fun setUnderrunCount(count: String) {
        text_underrun_count.text = count
    }

    override fun setProgress(percentPlayed: Int) {
        seek_playback_progress.progress = percentPlayed
    }

    override fun showPlaylist() {
        (activity as PlayerActivityView).onPlaylistFabClicked()
    }

    override fun setFabVisible() {
        button_fab.alpha = 1.0f
    }

    /**
     * OnSeekbarChangeListener
     */

    override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { }

    override fun onStartTrackingTouch(p0: SeekBar?) {
        presenter.onSeekbarTouch()
    }

    override fun onStopTrackingTouch(bar: SeekBar?) {
        presenter.onSeekbarRelease(bar?.progress ?: 0)
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

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun getPresenter(): FragmentPresenter {
        return presenter
    }

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

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.player"

        fun newInstance(): PlayerFragment {
            return PlayerFragment()
        }
    }
}
