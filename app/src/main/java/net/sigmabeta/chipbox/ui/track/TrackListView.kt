package net.sigmabeta.chipbox.ui.track

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface TrackListView : BaseView {
    fun setTracks(tracks: MutableList<Track>)

    fun setGames(games: HashMap<Long, Game>)

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