package net.sigmabeta.chipbox.view.adapter

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.GameListView
import net.sigmabeta.chipbox.view.viewholder.GameViewHolder

class GameGridAdapter(val view: GameListView) : BaseCursorAdapter() {
    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val card = LayoutInflater.from(parent?.context)
                ?.inflate(R.layout.grid_item_game, parent, false)

        if (card != null) {
            return GameViewHolder(card, this)
        } else {
            logError("[GameGridAdapter] Unable to inflate CardView...")
            return null
        }
    }

    override fun bind(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        (holder as GameViewHolder).bind(cursor)
    }

    fun onItemClick(id: Long) {
        view.onItemClick(id)
    }
}
