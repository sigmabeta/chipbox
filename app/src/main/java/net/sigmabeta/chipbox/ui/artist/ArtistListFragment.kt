package net.sigmabeta.chipbox.ui.artist

import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import io.realm.OrderedCollectionChangeSet
import kotlinx.android.synthetic.main.fragment_artist_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.ScanService
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.ItemListView
import net.sigmabeta.chipbox.ui.TopLevelFragment
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.track.TrackListFragment
import net.sigmabeta.chipbox.util.*
import javax.inject.Inject

class ArtistListFragment : BaseFragment<ArtistListPresenter, ArtistListView>(), ArtistListView, ItemListView<ArtistViewHolder>, TopLevelFragment {
    lateinit var presenter: ArtistListPresenter
        @Inject set

    val adapter = ArtistListAdapter(this)

    /**
     * ArtistListView
     */

    override fun launchNavActivity(id: String) {
        NavigationActivity.launch(activity, TrackListFragment.FRAGMENT_TAG, id)
    }

    override fun setArtists(artists: List<Artist>) {
        adapter.dataset = artists
    }

    override fun animateChanges(changeset: OrderedCollectionChangeSet) {
        adapter.processChanges(changeset)
    }

    override fun startRescan() {
        val intent = Intent(activity, ScanService::class.java)
        activity.startService(intent)
    }

    override fun showLoadingState() = ifVisible {
        list_artists.fadeOutPartially()
        loading_spinner.fadeIn().setDuration(50)
        layout_empty_state.fadeOutGone()
    }

    override fun showContent() = ifVisible {
        if (list_artists.visibility != VISIBLE) {
            list_artists.fadeIn()
            layout_empty_state.fadeOutGone()
            loading_spinner.fadeOutGone()
        }
    }

    override fun showEmptyState() = ifVisible {
        if (label_empty_state.visibility != VISIBLE) {
            layout_empty_state.visibility = View.VISIBLE
            label_empty_state.fadeInFromZero().setStartDelay(300)
            button_empty_state.fadeInFromZero().setStartDelay(600)
        }
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Int) {
        presenter.onItemClick(position)
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
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this) ?: activity.finish()
        }
    }

    override fun getContentLayout(): ViewGroup {
        return frame_content
    }

    override fun getPresenterImpl(): ArtistListPresenter {
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

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.artist_list"

        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}