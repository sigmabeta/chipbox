package net.sigmabeta.chipbox.view.fragment

import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_song_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.presenter.FragmentPresenter
import net.sigmabeta.chipbox.presenter.SongListPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.PlayerActivity
import net.sigmabeta.chipbox.view.adapter.SongListAdapter
import net.sigmabeta.chipbox.view.interfaces.NavigationFragment
import net.sigmabeta.chipbox.view.interfaces.SongListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import javax.inject.Inject

class SongListFragment : BaseFragment(), SongListView, TopLevelFragment, NavigationFragment {
    lateinit var presenter: SongListPresenter
        @Inject set

    var adapter: SongListAdapter? = null

    /**
     * SongListView
     */

    override fun setCursor(cursor: Cursor) {
        adapter?.changeCursor(cursor)
    }

    override fun onItemClick(track: Track, position: Int) {
        presenter.onItemClick(track, position)
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(activity)
    }

    override fun getCursor(): Cursor? {
        return adapter?.cursor
    }

    // TODO Instead of doing this, have the presenter pass the map to the fragment
    // TODO which can then pass the map to the adapter
    override fun getImagePath(gameId: Long): String? {
        return presenter.getImagePath(gameId)
    }

    override fun refreshList() {
        adapter?.notifyDataSetChanged()
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return list_songs?.isScrolledToBottom() ?: false
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

        val adapter = SongListAdapter(this, activity, true)
        this.adapter = adapter

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