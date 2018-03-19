package net.sigmabeta.chipbox.ui.playlist

import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.ListView

interface PlaylistFragmentView : ListView<Track, PlaylistTrackViewHolder> {
    fun onTrackMoved(originPos: Int, destPos: Int)

    fun onTrackRemoved(position: Int)

    fun updatePosition(position: Int?, oldPlayingPosition: Int): Unit

    fun scrollToPosition(position: Int, animate: Boolean)
}
