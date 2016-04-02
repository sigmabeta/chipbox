package net.sigmabeta.chipbox.ui.playlist

import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView
import java.util.*

class PlaylistAdapter(view: ItemListView<PlaylistTrackViewHolder>) : BaseArrayAdapter<Track, PlaylistTrackViewHolder>(view) {
    var playingTrackId: Long? = null
        set (value) {
            field = value
            notifyDataSetChanged()
        }

    var games: HashMap<Long, Game>? = null
        set (value) {
            field = value
            notifyDataSetChanged()
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
                    Collections.swap(dataset, originPos, destPos)
                    notifyItemMoved(originPos, destPos)
                    return true
                }
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
            viewHolder?.adapterPosition?.let { position ->
                dataset?.removeAt(position)
                notifyItemRemoved(position)
            }
        }
    }

    override fun getLayoutId() = R.layout.list_item_track_playlist

    override fun createViewHolder(view: View): PlaylistTrackViewHolder {
        return PlaylistTrackViewHolder(view, this)
    }

    override fun bind(holder: PlaylistTrackViewHolder, item: Track) {
        holder.bind(item)
    }

    fun onStartDrag(holder: PlaylistTrackViewHolder) {
        view.startDrag(holder)
    }
}