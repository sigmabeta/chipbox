package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_song.view.*
import kotlinx.android.synthetic.main.list_item_song_game.view.text_song_track_number
import net.sigmabeta.chipbox.model.database.*
import net.sigmabeta.chipbox.view.adapter.SongListAdapter


class SongViewHolder(val view: View, val adapter: SongListAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var trackId: Long? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Cursor) {
        trackId = toBind.getLong(COLUMN_DB_ID)

        val title = toBind.getString(COLUMN_TRACK_TITLE)
        val artistName = toBind.getString(COLUMN_TRACK_ARTIST)

        view.text_song_title.text = title
        view.text_song_artist.text = artistName

        if (view.image_game_box_art != null) {
            val gameId = toBind.getLong(COLUMN_TRACK_GAME_ID)
            val imagePath = adapter.imagesPath + gameId.toString() + "/local.png"

            Picasso.with(view.context)
                    .load(imagePath)
                    .centerCrop()
                    .fit()
                    .into(view.image_game_box_art)
        }

        if (view.text_song_track_number != null) {
            val trackNumber = toBind.getLong(COLUMN_TRACK_NUMBER)

            view.text_song_track_number.text = trackNumber.toString()
        }
    }


    override fun onClick(v: View) {
        adapter.onItemClick(trackId ?: return)
    }
}