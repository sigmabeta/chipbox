package net.sigmabeta.chipbox.ui.song

import android.view.View
import kotlinx.android.synthetic.main.list_item_song.view.image_game_box_art
import kotlinx.android.synthetic.main.list_item_song_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.loadImageLowQuality


class SongViewHolder(view: View, adapter: SongListAdapter) : BaseViewHolder<Track, SongViewHolder, SongListAdapter>(view, adapter), View.OnClickListener {
    var trackId: Long? = null

    var boundAtLeastOnce = false

    override fun getId(): Long? {
        return adapterPosition.toLong()
    }

    override fun bind(toBind: Track) {
        trackId = toBind.id

        view.text_song_title.text = toBind.title
        view.text_song_artist.text = toBind.artist
        view.text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        if (view.image_game_box_art != null) {
            val gameId = toBind.gameContainer?.toModel()?.id
            val imagePath = adapter.games?.get(gameId)?.artLocal

            if (imagePath != null) {
                view.image_game_box_art.loadImageLowQuality(imagePath, boundAtLeastOnce, true)
            } else {
                view.image_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, boundAtLeastOnce, true)
            }
        }

        if (view.text_song_track_number != null) {
            val trackNumber = toBind.trackNumber

            view.text_song_track_number.text = trackNumber.toString()
        }

        if (trackId == adapter.playingTrackId) {
            view.text_song_title.setTextAppearance(view.context, R.style.TextlistTrackTitlePlaying)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtistPlaying)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLengthPlaying)
            view.text_song_track_number?.setTextAppearance(view.context, R.style.TextListTrackNumberPlaying)
        } else {
            view.text_song_title.setTextAppearance(view.context, R.style.TextListTrackTitle)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtist)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLength)
            view.text_song_track_number?.setTextAppearance(view.context, R.style.TextListTrackNumber)
        }

        boundAtLeastOnce = true
    }
}