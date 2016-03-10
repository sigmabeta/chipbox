package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.list_item_song.view.image_game_box_art
import kotlinx.android.synthetic.main.list_item_song_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.database.*
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.loadImageLowQuality
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

        val trackLength = getTimeStringFromMillis(toBind.getLong(COLUMN_TRACK_LENGTH))

        view.text_song_title.text = title
        view.text_song_artist.text = artistName
        view.text_song_length.text = trackLength

        if (view.image_game_box_art != null) {
            val gameId = toBind.getLong(COLUMN_TRACK_GAME_ID)
            val imagePath = adapter.getImagePath(gameId)

            if (imagePath != null) {
                loadImageLowQuality(view.image_game_box_art, imagePath)
            } else {
                loadImageLowQuality(view.image_game_box_art, R.drawable.img_album_art_blank)
            }
        }

        if (view.text_song_track_number != null) {
            val trackNumber = toBind.getInt(COLUMN_TRACK_NUMBER)

            view.text_song_track_number.text = trackNumber.toString()
        }

        if (trackId == adapter.playingTrackId) {
            view.text_song_title.setTextColor(adapter.playingTextColor)
            view.text_song_artist.setTextColor(adapter.playingTextColor)
            view.text_song_length.setTextColor(adapter.playingTextColor)
            view.text_song_track_number?.setTextColor(adapter.playingTextColor)
        } else {
            view.text_song_title.setTextColor(adapter.primaryTextColor)
            view.text_song_artist.setTextColor(adapter.secondaryTextColor)
            view.text_song_length.setTextColor(adapter.primaryTextColor)
            view.text_song_track_number?.setTextColor(adapter.secondaryTextColor)
        }
    }


    override fun onClick(v: View) {
        adapter.onItemClick(trackId ?: return, adapterPosition)
    }
}