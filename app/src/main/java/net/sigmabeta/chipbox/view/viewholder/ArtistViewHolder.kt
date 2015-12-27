package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.list_item_artist.view.text_artist_name
import net.sigmabeta.chipbox.model.database.COLUMN_ARTIST_NAME
import net.sigmabeta.chipbox.model.database.COLUMN_DB_ID
import net.sigmabeta.chipbox.view.adapter.ArtistListAdapter

class ArtistViewHolder(val view: View, val adapter: ArtistListAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var artistId: Long? = null
    var artistName: String? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Cursor) {
        val artistName = toBind.getString(COLUMN_ARTIST_NAME)

        this.artistName = artistName
        artistId = toBind.getLong(COLUMN_DB_ID)

        view.text_artist_name.text = artistName
    }


    override fun onClick(v: View) {
        val artistName = this.artistName
        val artistId = this.artistId

        if (artistName != null && artistId != null) {
            adapter.onItemClick(artistId, artistName)
        }
    }
}