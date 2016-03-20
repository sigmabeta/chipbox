package net.sigmabeta.chipbox.ui.artist

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_artist_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.song.SongListFragment
import net.sigmabeta.chipbox.util.isScrolledToBottom
import javax.inject.Inject

class ArtistListFragment : BaseFragment(), ArtistListView, ItemListView<ArtistViewHolder>, TopLevelFragment {
    lateinit var presenter: ArtistListPresenter
        @Inject set

    val adapter = ArtistListAdapter(this)

    /**
     * ArtistListView
     */

    override fun launchNavActivity(id: Long) {
        NavigationActivity.launch(activity, SongListFragment.FRAGMENT_TAG, id)
    }

    override fun setArtists(artists: List<Artist>) {
        adapter.dataset = artists
    }

    /**
     * ItemListView
     */

    override fun onItemClick(id: Long, clickedViewHolder: ArtistViewHolder) {
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

    override fun getSharedImage(): View? = null

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.artist_list"

        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}