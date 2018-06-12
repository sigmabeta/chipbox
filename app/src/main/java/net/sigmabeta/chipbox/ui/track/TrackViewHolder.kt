package net.sigmabeta.chipbox.ui.track

import android.view.View
import kotlinx.android.synthetic.main.list_item_track.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.loadImageLowQuality

class TrackViewHolder(view: View, adapter: TrackListAdapter) : BaseViewHolder<Track, TrackViewHolder, TrackListAdapter>(view, adapter) {
    override fun bind(toBind: Track) {
       text_song_title.text = toBind.title
       text_song_artist.text = toBind.artistText
       text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        val imagePath = toBind.game?.artLocal

        if (imagePath != null) {
           image_track.loadImageLowQuality(imagePath, true, true)
        } else {
           image_track.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, true, true)
        }

        if (toBind.id == adapter.playingTrackId) {
           text_song_title.setTextAppearance(containerView.context, R.style.TextlistTrackTitlePlaying)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtistPlaying)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLengthPlaying)
        } else {
           text_song_title.setTextAppearance(containerView.context, R.style.TextListTrackTitle)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtist)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLength)
        }
    }
}
