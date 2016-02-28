package net.sigmabeta.chipbox.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_player.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.presenter.PlayerFragmentPresenter
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView
import javax.inject.Inject

class PlayerFragment : BaseFragment(), PlayerFragmentView {
    lateinit var presenter: PlayerFragmentPresenter
        @Inject set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter.onCreate()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater?.inflate(R.layout.fragment_player, container, false)

        return rootView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.onViewCreated()

        button_play.setOnClickListener {
            presenter.onFabClick()
        }

        button_skip_forward.setOnClickListener {
            presenter.onNextClick()
        }

        button_skip_back.setOnClickListener {
            presenter.onRewindClick()
        }
    }

    override fun onResume() {
        super.onResume()

        presenter.onResume()
    }

    override fun onPause() {
        super.onPause()

        presenter.onPause()
    }

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

    override fun setGameBoxart(gameId: Long) {
        val imagesFolderPath = "file://" + activity.getExternalFilesDir(null).absolutePath + "/images/"
        val imagePath = imagesFolderPath + gameId.toString() + "/local.png"

        Picasso.with(context)
                .load(imagePath)
                .centerCrop()
                .fit()
                .error(R.drawable.img_album_art_blank)
                .into(image_game_box_art)
    }

    override fun showPauseButton() {
        button_play.setImageResource(R.drawable.ic_pause_white_48dp)
    }

    override fun showPlayButton() {
        button_play.setImageResource(R.drawable.ic_play_48dp)
    }

    override fun setUnderrunCount(count: String) {
        text_underrun_count.text = count
    }

    /**
     * BaseFragment
     */

    override fun inject() {
        FragmentInjector.inject(this)
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun getTitle(): String {
        return ""
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.player"

        fun newInstance(): PlayerFragment {
            return PlayerFragment()
        }
    }
}
