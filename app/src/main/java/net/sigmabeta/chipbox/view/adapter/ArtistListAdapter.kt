package net.sigmabeta.chipbox.view.adapter

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import net.sigmabeta.chipbox.view.viewholder.ArtistViewHolder

class ArtistListAdapter(val view: ArtistListView) : BaseCursorAdapter() {
    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val row = LayoutInflater.from(parent?.context)
                ?.inflate(R.layout.list_item_artist, parent, false)

        if (row != null) {
            return ArtistViewHolder(row, this)
        } else {
            logError("[ArtistListAdapter] Unable to inflate row...")
            return null
        }
    }

    override fun bind(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        (holder as ArtistViewHolder).bind(cursor)
    }

    fun onItemClick(id: Long) {
        view.onItemClick(id)
    }
}