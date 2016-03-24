package net.sigmabeta.chipbox.ui.song

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView
import java.util.*

class SongListAdapter(view: ItemListView<SongViewHolder>, val showArt: Boolean, val withHeader: Boolean = false) : BaseArrayAdapter<Track, SongViewHolder>(view) {
    var game: Game? = null
        set (value) {
            field = value
            if (value != null) {
                notifyItemChanged(0)
            }
        }

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

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        getItem(position)?.let {
            bind(holder, it)
        } ?: let {
            if (position == 0 && holder is GameHeaderViewHolder) {
                holder.bind()
            }
        }
    }

    override fun createHeaderViewHolder(view: View): SongViewHolder? {
        return GameHeaderViewHolder(view, this)
    }

    override fun getHeaderLayoutId(): Int {
        return if (withHeader) {
            R.layout.list_header_game
        } else {
            0
        }
    }

    override fun createViewHolder(view: View): SongViewHolder {
        return SongViewHolder(view, this)
    }

    override fun bind(holder: SongViewHolder, item: Track) {
        holder.bind(item)
    }
}