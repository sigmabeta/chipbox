package net.sigmabeta.chipbox.view.adapter

import android.content.Context
import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.SongListView
import net.sigmabeta.chipbox.view.viewholder.SongViewHolder
import java.util.*

class SongListAdapter(val view: SongListView,
                      val context: Context,
                      val showArt: Boolean) : BaseCursorAdapter() {

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

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val row = LayoutInflater.from(parent?.context)
                ?.inflate(
                        if (showArt)
                            R.layout.list_item_song
                        else
                            R.layout.list_item_song_game,
                        parent, false)

        if (row != null) {
            return SongViewHolder(row, this)
        } else {
            logError("[SongListAdapter] Unable to inflate row...")
            return null
        }
    }

    override fun bind(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        (holder as SongViewHolder).bind(cursor)
    }

    fun onItemClick(id: Long, position: Int) {
        val localCursor = cursor

        if (localCursor != null) {
            localCursor.moveToPosition(position)

            val track = Track.fromCursor(localCursor)
            view.onItemClick(track, position)
        }
    }
}