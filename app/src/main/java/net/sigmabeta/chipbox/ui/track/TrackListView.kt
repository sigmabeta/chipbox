package net.sigmabeta.chipbox.ui.track

import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface TrackListView : BaseView {
    fun setTracks(tracks: List<Track>)

    fun refreshList()

    fun setActivityTitle(title: String)

    fun showRescanScreen()

    fun showContent()

    fun showEmptyState()

    fun onTrackLoadError()
}