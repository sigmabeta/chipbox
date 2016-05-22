package net.sigmabeta.chipbox.ui.artist

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_artist_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.main.MainView
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.track.TrackListFragment
import net.sigmabeta.chipbox.util.fadeIn
import net.sigmabeta.chipbox.util.fadeOut
import net.sigmabeta.chipbox.util.fadeOutPartially
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
        NavigationActivity.launch(activity, TrackListFragment.FRAGMENT_TAG, id)
    }

    override fun setArtists(artists: MutableList<Artist>) {
        adapter.dataset = artists
    }

    override fun showFilesScreen() {
        val mainActivity = activity
        if (mainActivity is MainView) {
            mainActivity.launchFileListActivity()
        }
    }

    override fun showLoadingSpinner() = ifVisible {
        loading_spinner.fadeIn()
    }

    override fun hideLoadingSpinner() = ifVisible {
        loading_spinner.fadeOut()
    }

    override fun showContent() = ifVisible {
        list_artists.fadeIn()
    }

    override fun hideContent() = ifVisible {
        list_artists.fadeOutPartially()
    }

    override fun showEmptyState() = ifVisible {
        layout_empty_state.visibility = View.VISIBLE
        label_empty_state.fadeIn().setStartDelay(300)
        button_empty_state.fadeIn().setStartDelay(600)
    }

    override fun hideEmptyState() = ifVisible {
        layout_empty_state.fadeOut().withEndAction {
            label_empty_state.alpha = 0.0f
            button_empty_state.alpha = 0.0f
        }
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

    override fun refresh() = presenter.refresh()

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

    override fun getContentLayout(): ViewGroup {
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

        button_empty_state.setOnClickListener(this)
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