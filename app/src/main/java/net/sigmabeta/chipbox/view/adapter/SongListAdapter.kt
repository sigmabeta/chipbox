package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.SongViewHolder
import java.util.*

class SongListAdapter(view: ItemListView, val showArt: Boolean) : BaseArrayAdapter<Track, SongViewHolder>(view) {
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