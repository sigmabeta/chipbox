package net.sigmabeta.chipbox.view.fragment

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_song_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.presenter.FragmentPresenter
import net.sigmabeta.chipbox.presenter.SongListPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.BaseActivity
import net.sigmabeta.chipbox.view.activity.PlayerActivity
import net.sigmabeta.chipbox.view.adapter.SongListAdapter
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.interfaces.NavigationFragment
import net.sigmabeta.chipbox.view.interfaces.SongListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import java.util.*
import javax.inject.Inject

class SongListFragment : BaseFragment(), SongListView, ItemListView, TopLevelFragment, NavigationFragment {
    lateinit var presenter: SongListPresenter
        @Inject set

    var adapter = SongListAdapter(this, true)

    /**
     * SongListView
     */

    override fun setSongs(songs: ArrayList<Track>) {
        adapter.dataset = songs
    }

    override fun setGames(games: HashMap<Long, Game>) {
        adapter.games = games
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(activity)
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

    override fun onItemClick(position: Long) {
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

    override fun getTitle(): String {
        return getString(R.string.app_name)
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

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.song_list"

        val ARGUMENT_ARTIST = "${FRAGMENT_TAG}.artist"

        fun newInstance(artist: Long): SongListFragment {
            val fragment = SongListFragment()

            val arguments = Bundle()
            arguments.putLong(ARGUMENT_ARTIST, artist)

            fragment.arguments = arguments
            return fragment
        }
    }
}