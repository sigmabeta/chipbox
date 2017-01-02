package net.sigmabeta.chipbox.ui.track

import android.view.View
import kotlinx.android.synthetic.main.list_item_track.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.loadImageLowQuality

class TrackViewHolder(view: View, adapter: TrackListAdapter) : BaseViewHolder<Track, TrackViewHolder, TrackListAdapter>(view, adapter) {
    override fun getId(): Long? {
        return adapterPosition.toLong()
    }

    override fun bind(toBind: Track) {
        view.text_song_title.text = toBind.title
        view.text_song_artist.text = toBind.artistText
        view.text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        val imagePath = toBind.game?.artLocal

        if (imagePath != null) {
            view.image_game_box_art.loadImageLowQuality(imagePath, true, true)
        } else {
            view.image_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, true, true)
        }

        if (toBind.id == adapter.playingTrackId) {
            view.text_song_title.setTextAppearance(view.context, R.style.TextlistTrackTitlePlaying)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtistPlaying)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLengthPlaying)
        } else {
            view.text_song_title.setTextAppearance(view.context, R.style.TextListTrackTitle)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtist)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLength)
        }
    }
}
