package net.sigmabeta.chipbox.ui.song

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView
import java.util.*

class SongListAdapter(view: ItemListView<SongViewHolder>, val showArt: Boolean) : BaseArrayAdapter<Track, SongViewHolder>(view) {
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

    override fun getLayoutId(): Int {
        return if (showArt)
            R.layout.list_item_song
        else
            R.layout.list_item_song_game
    }

    override fun createViewHolder(view: View): SongViewHolder {
        return SongViewHolder(view, this)
    }

    override fun bind(holder: SongViewHolder, item: Track) {
        holder.bind(item)
    }
}