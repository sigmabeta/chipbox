package net.sigmabeta.chipbox.view.fragment

import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_artist_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.presenter.ArtistListPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.NavigationActivity
import net.sigmabeta.chipbox.view.adapter.ArtistListAdapter
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import javax.inject.Inject

class ArtistListFragment : BaseFragment(), ArtistListView, TopLevelFragment {
    var presenter: ArtistListPresenter? = null
        @Inject set

    val adapter = ArtistListAdapter(this)

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater?.inflate(R.layout.fragment_artist_list, container, false)

        presenter?.onCreateView()

        return rootView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)

        list_artists.adapter = adapter
        list_artists.layoutManager = layoutManager
    }

    /**
     * ArtistListView
     */

    override fun setCursor(cursor: Cursor) {
        adapter.changeCursor(cursor)
    }

    override fun onItemClick(id: Long, artistName: String) {
        NavigationActivity.launch(activity,
                SongListFragment.FRAGMENT_TAG,
                id,
                artistName)
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return list_artists?.isScrolledToBottom() ?: false
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
        return "Artists"
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.artist_list"

        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}