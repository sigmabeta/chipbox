package net.sigmabeta.chipbox.ui.track

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface TrackListView : BaseView {
    fun setSongs(songs: List<Track>)

    fun setGames(games: HashMap<Long, Game>)

    fun refreshList()

    fun setActivityTitle(name: String)
}