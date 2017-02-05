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
import net.sigmabeta.chipbox.util.*
import javax.inject.Inject

class TrackListFragment : BaseFragment<TrackListPresenter, TrackListView>(), TrackListView, ItemListView<TrackViewHolder>, TopLevelFragment, NavigationFragment {
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

    override fun showContent() = ifVisible {
        list_tracks.fadeIn()
        layout_empty_state.fadeOutGone()
    }

    override fun showEmptyState() = ifVisible {
        layout_empty_state.visibility = View.VISIBLE
        label_empty_state.fadeInFromZero().setStartDelay(300)
        button_empty_state.fadeInFromZero().setStartDelay(600)
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

    override fun onItemClick(position: Int, clickedViewHolder: TrackViewHolder) {
        presenter.onItemClick(position)
    }

    /**
     * BaseFragment
     */

    override fun showLoading() = ifVisible {
        loading_spinner.fadeIn().setDuration(50)
        list_tracks.fadeOutPartially()
        layout_empty_state.fadeOutGone()
    }

    override fun hideLoading() = ifVisible {
        loading_spinner.fadeOutGone()
    }

    override fun inject() {
        val container = activity
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this)
        }
    }

    override fun getContentLayout(): ViewGroup {
        return frame_content
    }

    override fun getPresenterImpl() = presenter

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

        fun newInstance(id: String?): TrackListFragment {
            val fragment = TrackListFragment()

            val arguments = Bundle()
            arguments.putString(ARGUMENT_ARTIST, id)

            fragment.arguments = arguments
            return fragment
        }
    }
}