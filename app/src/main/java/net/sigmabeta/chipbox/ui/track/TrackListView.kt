package net.sigmabeta.chipbox.ui.track

import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface TrackListView : BaseView {
    fun setTracks(tracks: List<Track>)

    fun refreshList()

    fun setActivityTitle(name: String)

    fun showFilesScreen()

    fun showLoadingSpinner()

    fun hideLoadingSpinner()

    fun showContent()

    fun hideContent()

    fun showEmptyState()

    fun hideEmptyState()

    fun onTrackLoadError()
}