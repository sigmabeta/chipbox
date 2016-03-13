package net.sigmabeta.chipbox.view.fragment

import android.support.v7.widget.LinearLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_artist_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Artist
import net.sigmabeta.chipbox.presenter.ArtistListPresenter
import net.sigmabeta.chipbox.presenter.FragmentPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.BaseActivity
import net.sigmabeta.chipbox.view.activity.NavigationActivity
import net.sigmabeta.chipbox.view.adapter.ArtistListAdapter
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import java.util.*
import javax.inject.Inject

class ArtistListFragment : BaseFragment(), ArtistListView, ItemListView, TopLevelFragment {
    lateinit var presenter: ArtistListPresenter
        @Inject set

    val adapter = ArtistListAdapter(this)

    /**
     * ArtistListView
     */

    override fun launchNavActivity(id: Long) {
        NavigationActivity.launch(activity, SongListFragment.FRAGMENT_TAG, id)
    }

    override fun setArtists(artists: ArrayList<Artist>) {
        adapter.dataset = artists
    }

    /**
     * ItemListView
     */

    override fun onItemClick(id: Long) {
        presenter.onItemClick(id)
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
        return R.layout.fragment_artist_list
    }

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(activity)

        list_artists.adapter = adapter
        list_artists.layoutManager = layoutManager
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.artist_list"

        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}