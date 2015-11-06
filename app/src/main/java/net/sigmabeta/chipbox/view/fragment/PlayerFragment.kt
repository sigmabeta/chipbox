package net.sigmabeta.chipbox.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.fragment_player.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.presenter.PlayerFragmentPresenter
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView
import javax.inject.Inject

class PlayerFragment : BaseFragment(), PlayerFragmentView {
    var presenter: PlayerFragmentPresenter? = null
        @Inject set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trackId = arguments.getLong(ARGUMENT_TRACK_ID)

        presenter?.onCreate(trackId)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater?.inflate(R.layout.fragment_player, container, false)

        presenter?.onCreateView()

        return rootView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_play.setOnClickListener {
            presenter?.onFabClick()
        }
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

    override fun setGameBoxart(gameId: Long) {
        val imagesFolderPath = "file://" + activity.getExternalFilesDir(null).absolutePath + "/images/"
        val imagePath = imagesFolderPath + gameId.toString() + "/local.png"

        Picasso.with(context)
                .load(imagePath)
                .centerCrop()
                .fit()
                .into(image_game_box_art)
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

        val ARGUMENT_TRACK_ID = "${FRAGMENT_TAG}.track_id"

        fun newInstance(trackId: Long): PlayerFragment {
            val fragment = PlayerFragment()

            val arguments = Bundle()
            arguments.putLong(ARGUMENT_TRACK_ID, trackId)


            fragment.arguments = arguments
            return fragment
        }
    }
}
