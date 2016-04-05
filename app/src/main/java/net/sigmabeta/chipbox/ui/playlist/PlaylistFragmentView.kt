package net.sigmabeta.chipbox.ui.playlist

import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface PlaylistFragmentView : BaseView {
    fun showQueue(queue: MutableList<Track>)

    fun onTrackMoved(originPos: Int, destPos: Int)

    fun onTrackRemoved(position: Int)

    fun updatePosition(position: Int?, oldPlayingPosition: Int): Unit
}
