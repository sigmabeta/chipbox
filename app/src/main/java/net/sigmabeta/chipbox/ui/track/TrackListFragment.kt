package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_song_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.main.MainView
import net.sigmabeta.chipbox.util.fadeIn
import net.sigmabeta.chipbox.util.fadeOut
import net.sigmabeta.chipbox.util.fadeOutPartially
import net.sigmabeta.chipbox.util.isScrolledToBottom
import javax.inject.Inject

class TrackListFragment : BaseFragment(), TrackListView, ItemListView<TrackViewHolder>, TopLevelFragment, NavigationFragment {
    lateinit var presenter: TrackListPresenter
        @Inject set

    var adapter = TrackListAdapter(this)

    /**
     * SongListView
     */

    override fun setTracks(tracks: List<Track>) {
        adapter.dataset = tracks
    }

    override fun refreshList() {
        adapter.notifyDataSetChanged()
    }

    override fun showFilesScreen() {
        val mainActivity = activity
        if (mainActivity is MainView) {
            mainActivity.launchFileListActivity()
        }
    }

    override fun showLoadingSpinner() = ifVisible {
        loading_spinner.fadeIn().setDuration(50)
    }

    override fun hideLoadingSpinner() = ifVisible {
        loading_spinner.fadeOut()
    }

    override fun showContent() = ifVisible {
        list_tracks.fadeIn()
    }

    override fun hideContent() = ifVisible {
        list_tracks.fadeOutPartially()
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

    override fun onTrackLoadError() {
        showToastMessage("Error loading tracks.")
        activity.finish()
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return list_tracks?.isScrolledToBottom() ?: false
    }

    override fun refresh() = presenter.refresh(arguments)

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

    override fun getContentLayout(): ViewGroup {
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

        list_tracks.adapter = adapter
        list_tracks.layoutManager = layoutManager

        button_empty_state.setOnClickListener(this)
    }

    override fun getSharedImage(): View? = null

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.track_list"

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