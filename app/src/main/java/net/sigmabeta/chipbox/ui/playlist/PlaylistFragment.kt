package net.sigmabeta.chipbox.ui.playlist

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import kotlinx.android.synthetic.main.fragment_playlist.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.ItemListView
import net.sigmabeta.chipbox.util.TRANSITION_FRAGMENT_FADE_IN_BELOW
import net.sigmabeta.chipbox.util.TRANSITION_FRAGMENT_FADE_OUT_DOWN
import javax.inject.Inject

@ActivityScoped
class PlaylistFragment : BaseFragment(), PlaylistFragmentView, ItemListView<PlaylistTrackViewHolder> {
    lateinit var presenter: PlaylistFragmentPresenter
        @Inject set

    var adapter = PlaylistAdapter(this)

    val touchCallback = object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, swipeFlags)
        }

        override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean {
            viewHolder?.adapterPosition?.let { originPos ->
                target?.adapterPosition?.let { destPos ->
                    presenter.onTrackMoved(originPos, destPos)
                    return true
                }
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
            viewHolder?.adapterPosition?.let { position ->
                presenter.onTrackRemoved(position)
            }
        }

        override fun isLongPressDragEnabled() = false
    }

    val touchHelper = ItemTouchHelper(touchCallback)

    /**
     * PlaylistFragmentView
     */

    override fun showQueue(queue: MutableList<Track>) {
        adapter.dataset = queue
    }

    override fun onTrackMoved(originPos: Int, destPos: Int) {
        adapter.notifyItemMoved(originPos, destPos)
    }

    override fun onTrackRemoved(position: Int) {
        adapter.notifyItemRemoved(position)
    }

    override fun updatePosition(position: Int?, oldPlayingPosition: Int) {
        adapter.playingPosition = position ?: -1
        if (oldPlayingPosition >= 0) {
            adapter.notifyItemChanged(oldPlayingPosition)
        }
    }

    override fun scrollToPosition(position: Int, animate: Boolean) {
        if (animate) {
            recycler_playlist.smoothScrollToPosition(position)
        } else {
            recycler_playlist.scrollToPosition(position)
        }
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Long, clickedViewHolder: PlaylistTrackViewHolder) {
        presenter.onItemClick(position)
    }

    override fun startDrag(holder: PlaylistTrackViewHolder) {
        touchHelper.startDrag(holder)
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
        recycler_playlist.setPadding(0, getStatusBarHeight(), 0, 0)

        touchHelper.attachToRecyclerView(recycler_playlist)
    }

    override fun getFragmentTag() = "${BuildConfig.APPLICATION_ID}.playlist"

    /**
     * Private Methods
     */

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")

        if (id > 0) {
            return resources.getDimensionPixelSize(id)
        }

        return 0
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.playlist"

        fun newInstance(): PlaylistFragment {
            val fragment = PlaylistFragment()

            fragment.enterTransition = TRANSITION_FRAGMENT_FADE_IN_BELOW
            fragment.returnTransition = TRANSITION_FRAGMENT_FADE_OUT_DOWN

            return fragment
        }
    }
}

