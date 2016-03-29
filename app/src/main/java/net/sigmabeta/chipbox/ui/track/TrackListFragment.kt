package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_song_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.util.isScrolledToBottom
import java.util.*
import javax.inject.Inject

class TrackListFragment : BaseFragment(), TrackListView, ItemListView<TrackViewHolder>, TopLevelFragment, NavigationFragment {
    lateinit var presenter: TrackListPresenter
        @Inject set

    var adapter = TrackListAdapter(this, true)

    /**
     * SongListView
     */

    override fun setSongs(songs: List<Track>) {
        adapter.dataset = songs
    }

    override fun setGames(games: HashMap<Long, Game>) {
        adapter.games = games
    }

    override fun refreshList() {
        adapter.notifyDataSetChanged()
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return list_songs?.isScrolledToBottom() ?: false
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Long, clickedViewHolder: TrackViewHolder) {
        presenter.onItemClick(position)
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
        return R.layout.fragment_song_list
    }

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(activity)

        list_songs.adapter = adapter
        list_songs.layoutManager = layoutManager
    }

    override fun getSharedImage(): View? = null

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.song_list"

        val ARGUMENT_ARTIST = "${FRAGMENT_TAG}.artist"

        fun newInstance(artist: Long): TrackListFragment {
            val fragment = TrackListFragment()

            val arguments = Bundle()
            arguments.putLong(ARGUMENT_ARTIST, artist)

            fragment.arguments = arguments
            return fragment
        }
    }
}