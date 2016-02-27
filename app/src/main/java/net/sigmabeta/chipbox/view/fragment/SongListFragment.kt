package net.sigmabeta.chipbox.view.fragment

import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_song_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.presenter.SongListPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.PlayerActivity
import net.sigmabeta.chipbox.view.adapter.SongListAdapter
import net.sigmabeta.chipbox.view.interfaces.NavigationFragment
import net.sigmabeta.chipbox.view.interfaces.SongListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import javax.inject.Inject

class SongListFragment : BaseFragment(), SongListView, TopLevelFragment, NavigationFragment {
    var presenter: SongListPresenter? = null
        @Inject set

    var adapter: SongListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val artist = arguments.getLong(ARGUMENT_ARTIST)

        presenter?.onCreate(artist)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater?.inflate(R.layout.fragment_song_list, container, false)

        presenter?.onCreateView()

        return rootView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)

        val adapter = SongListAdapter(this, activity, true)
        this.adapter = adapter

        list_songs.adapter = adapter
        list_songs.layoutManager = layoutManager
    }

    /**
     * SongListView
     */

    override fun setCursor(cursor: Cursor) {
        adapter?.changeCursor(cursor)
    }

    override fun onItemClick(track: Track, position: Int) {
        presenter?.onItemClick(track, position)
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(activity)
    }

    override fun getCursor(): Cursor? {
        return adapter?.cursor
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