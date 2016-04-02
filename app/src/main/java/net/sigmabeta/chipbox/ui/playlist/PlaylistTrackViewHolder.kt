package net.sigmabeta.chipbox.ui.playlist

import android.support.v4.view.MotionEventCompat
import android.view.MotionEvent
import android.view.View
import kotlinx.android.synthetic.main.list_item_track_playlist.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.getTimeStringFromMillis

class PlaylistTrackViewHolder(view: View, adapter: PlaylistAdapter) : BaseViewHolder<Track, PlaylistTrackViewHolder, PlaylistAdapter>(view, adapter), View.OnTouchListener {
    init {
        view.handle_track.setOnTouchListener(this)
        view.relative_clickable.setOnClickListener(this)
        view.setOnClickListener(null)
    }

    override fun getId(): Long? {
        return adapterPosition.toLong()
    }

    override fun bind(toBind: Track) {
        view.text_song_title.text = toBind.title
        view.text_song_artist.text = toBind.artistText
        view.text_song_length.text = getTimeStringFromMillis(toBind.trackLength ?: 0)

        val gameId = toBind.gameContainer?.toModel()?.id
        val imagePath = adapter.games?.get(gameId)?.artLocal

        if (adapterPosition == adapter.playingPosition) {
            view.text_song_title.setTextAppearance(view.context, R.style.TextlistTrackTitlePlaying)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtistPlaying)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLengthPlaying)
        } else {
            view.text_song_title.setTextAppearance(view.context, R.style.TextListTrackTitle)
            view.text_song_artist.setTextAppearance(view.context, R.style.TextListTrackArtist)
            view.text_song_length.setTextAppearance(view.context, R.style.TextListTrackLength)
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