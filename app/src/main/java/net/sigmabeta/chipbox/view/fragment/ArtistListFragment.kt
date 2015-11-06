package net.sigmabeta.chipbox.view.fragment

import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.fragment_artist_list.frame_content
import kotlinx.android.synthetic.fragment_artist_list.list_artists
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.presenter.ArtistListPresenter
import net.sigmabeta.chipbox.view.activity.NavigationActivity
import net.sigmabeta.chipbox.view.adapter.ArtistListAdapter
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import javax.inject.Inject

class ArtistListFragment : BaseFragment(), ArtistListView {
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

    override fun onItemClick(id: Long) {
        NavigationActivity.launch(activity,
                SongListFragment.FRAGMENT_TAG,
                id)
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
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.artist_list"

        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}