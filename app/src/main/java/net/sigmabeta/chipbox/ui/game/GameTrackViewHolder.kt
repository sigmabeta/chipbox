package net.sigmabeta.chipbox.ui.game

import android.view.View
import kotlinx.android.synthetic.main.list_item_track_game.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis


open class GameTrackViewHolder(view: View, adapter: GameTrackListAdapter) : BaseViewHolder<Track, GameTrackViewHolder, GameTrackListAdapter>(view, adapter) {
    override fun bind(toBind: Track) {
       text_song_title.text = toBind.title
       text_song_artist.text = toBind.artistText
       text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        if (text_song_track_number != null) {
            val trackNumber = toBind.trackNumber

           text_song_track_number.text = trackNumber.toString()
        }

        if (toBind.id == adapter.playingTrackId) {
           text_song_title.setTextAppearance(containerView.context, R.style.TextlistTrackTitlePlaying)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtistPlaying)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLengthPlaying)
           text_song_track_number?.setTextAppearance(containerView.context, R.style.TextListTrackNumberPlaying)
        } else {
           text_song_title.setTextAppearance(containerView.context, R.style.TextListTrackTitle)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtist)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLength)
           text_song_track_number?.setTextAppearance(containerView.context, R.style.TextListTrackNumber)
        }
    }
}