package net.sigmabeta.chipbox.ui.playlist

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

    override fun getLayoutId() = R.layout.list_item_track_playlist

    override fun createViewHolder(view: View): PlaylistTrackViewHolder {
        return PlaylistTrackViewHolder(view, this)
    }

    override fun bind(holder: PlaylistTrackViewHolder, item: Track) {
        holder.bind(item)
    }

}