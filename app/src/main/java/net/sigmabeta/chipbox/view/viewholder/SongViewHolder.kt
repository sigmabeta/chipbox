package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.grid_item_game.view.image_game_box_art
import kotlinx.android.synthetic.list_item_song.view.text_song_artist
import kotlinx.android.synthetic.list_item_song.view.text_song_title
import net.sigmabeta.chipbox.model.database.COLUMN_DB_ID
import net.sigmabeta.chipbox.model.database.COLUMN_TRACK_ARTIST
import net.sigmabeta.chipbox.model.database.COLUMN_TRACK_GAME_ID
import net.sigmabeta.chipbox.model.database.COLUMN_TRACK_TITLE
import net.sigmabeta.chipbox.view.adapter.SongListAdapter


class SongViewHolder(val view: View, val adapter: SongListAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var trackId: Long? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Cursor) {
        val title = toBind.getString(COLUMN_TRACK_TITLE)
        val artistName = toBind.getString(COLUMN_TRACK_ARTIST)
        trackId = toBind.getLong(COLUMN_DB_ID)

        view.text_song_title.text = title
        view.text_song_artist.text = artistName

        val gameId = toBind.getLong(COLUMN_TRACK_GAME_ID)

        val imagePath = adapter.imagesPath + gameId.toString() + "/local.png"
        Picasso.with(view.context)
                .load(imagePath)
                .centerCrop()
                .fit()
                .into(view.image_game_box_art)
    }


    override fun onClick(v: View) {
        adapter.onItemClick(trackId ?: return)
    }
}