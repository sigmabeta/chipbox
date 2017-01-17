package net.sigmabeta.chipbox.ui.game

import android.view.View
import kotlinx.android.synthetic.main.list_item_track_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis


open class GameTrackViewHolder(view: View, adapter: GameTrackListAdapter) : BaseViewHolder<Track, GameTrackViewHolder, GameTrackListAdapter>(view, adapter) {
    override fun bind(toBind: Track) {
        view.text_song_title.text = toBind.title
        view.text_song_artist.text = toBind.artistText
        view.text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        if (view.text_song_track_number != null) {
            val trackNumber = toBind.trackNumber

            view.text_song_track_number.text = trackNumber.toString()
        }

        if (toBind.id == adapter.playingTrackId) {
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
    }
}