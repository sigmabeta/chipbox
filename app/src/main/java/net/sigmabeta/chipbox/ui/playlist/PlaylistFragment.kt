package net.sigmabeta.chipbox.ui.playlist

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import timber.log.Timber

@ActivityScoped
class PlaylistFragment : ListFragment<PlaylistFragmentPresenter, PlaylistFragmentView, Track, PlaylistTrackViewHolder, PlaylistAdapter>(), PlaylistFragmentView {

    /**
     * PlaylistFragmentView
     */

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
            recycler_list.smoothScrollToPosition(position)
        } else {
            recycler_list.scrollToPosition(position)
        }
    }

    /**
     * ListFragment
     */

    override fun createAdapter() = PlaylistAdapter(this)

    /**
     * BaseFragment
     */

    override fun inject(): Boolean {
        val container = activity
        if (container is BaseActivity<*, *>) {container.getFragmentComponent()?.let {


                it.inject(this)
                return true
            } ?: let {
                Timber.e("${className()} injection failure: ${container?.className()}'s FragmentComponent not valid.")
                return false
            }
        } else {
            Timber.e("${className()} injection failure: ${container?.className()} not valid.")
            return false
        }
    }

    override fun configureViews() {
        super.configureViews()

        val layoutManager = LinearLayoutManager(activity)

        recycler_list.adapter = adapter
        recycler_list.layoutManager = layoutManager
        recycler_list.setPadding(0, getStatusBarHeight(), 0, 0)

        touchHelper.attachToRecyclerView(recycler_list)
    }

    override fun getFragmentTag() = FRAGMENT_TAG

    /**
     * Implementation Details
     */

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")

        if (id > 0) {
            return resources.getDimensionPixelSize(id)
        }

        return 0
    }

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

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.playlist"

        fun newInstance(): PlaylistFragment {
            val fragment = PlaylistFragment()

            return fragment
        }
    }
}

