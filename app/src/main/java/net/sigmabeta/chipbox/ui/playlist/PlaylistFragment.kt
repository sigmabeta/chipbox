package net.sigmabeta.chipbox.ui.playlist

import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_playlist.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.ItemListView
import javax.inject.Inject

@ActivityScoped
class PlaylistFragment : BaseFragment(), PlaylistFragmentView, ItemListView<PlaylistTrackViewHolder> {
    lateinit var presenter: PlaylistFragmentPresenter
        @Inject set

    var adapter = PlaylistAdapter(this)

    /**
     * PlaylistFragmentView
     */

    override fun showQueue(queue: List<Track>) {
        adapter.dataset = queue
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Long, clickedViewHolder: PlaylistTrackViewHolder) {
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

    override fun getPresenter(): FragmentPresenter = presenter

    override fun getLayoutId() = R.layout.fragment_playlist

    override fun getContentLayout() = frame_content

    override fun getSharedImage() = null

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(activity)

        recycler_playlist.adapter = adapter
        recycler_playlist.layoutManager = layoutManager
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.playlist"

        fun newInstance(): PlaylistFragment {
            val fragment = PlaylistFragment()
            return fragment
        }
    }
}

