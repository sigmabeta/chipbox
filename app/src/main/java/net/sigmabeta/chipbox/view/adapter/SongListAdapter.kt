package net.sigmabeta.chipbox.view.adapter

import android.content.Context
import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.SongListView
import net.sigmabeta.chipbox.view.viewholder.SongViewHolder

class SongListAdapter(val view: SongListView, val context: Context) : BaseCursorAdapter() {
    val imagesPath = "file://" + context.getExternalFilesDir(null).absolutePath + "/images/"

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val row = LayoutInflater.from(parent?.context)
                ?.inflate(R.layout.list_item_song, parent, false)

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

    fun onItemClick(id: Long) {
        view.onItemClick(id)
    }
}