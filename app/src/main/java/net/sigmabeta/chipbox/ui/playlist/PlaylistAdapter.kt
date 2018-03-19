package net.sigmabeta.chipbox.ui.playlist

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ListView
import java.util.*

class PlaylistAdapter(view: ListView<Track, PlaylistTrackViewHolder>) : BaseArrayAdapter<Track, PlaylistTrackViewHolder>(view) {
    var playingPosition: Int = -1
        set (value) {
            val oldPosition = field
            field = value

            notifyItemChanged(oldPosition)
            notifyItemChanged(value)
        }

    var games: HashMap<Long, Game>? = null
        set (value) {
            field = value
            notifyDataSetChanged()
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