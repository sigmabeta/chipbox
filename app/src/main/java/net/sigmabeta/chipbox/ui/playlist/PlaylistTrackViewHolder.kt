package net.sigmabeta.chipbox.ui.playlist

import android.support.v4.view.MotionEventCompat
import android.view.MotionEvent
import android.view.View
import kotlinx.android.synthetic.main.list_item_track_playlist.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis

class PlaylistTrackViewHolder(view: View, adapter: PlaylistAdapter) : BaseViewHolder<Track, PlaylistTrackViewHolder, PlaylistAdapter>(view, adapter), View.OnTouchListener {
    init {
       handle_track.setOnTouchListener(this)
    }

    override fun bind(toBind: Track) {
       text_song_title.text = toBind.title
       text_song_artist.text = toBind.artistText
       text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        if (adapterPosition == adapter.playingPosition) {
           text_song_title.setTextAppearance(containerView.context, R.style.TextlistTrackTitlePlaying)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtistPlaying)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLengthPlaying)
        } else {
           text_song_title.setTextAppearance(containerView.context, R.style.TextListTrackTitle)
           text_song_artist.setTextAppearance(containerView.context, R.style.TextListTrackArtist)
           text_song_length.setTextAppearance(containerView.context, R.style.TextListTrackLength)
        }
    }

    override fun onTouch(touched: View?, event: MotionEvent?): Boolean {
        if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
            adapter.onStartDrag(this)
            return true
        }
        return false
    }
}