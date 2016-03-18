package net.sigmabeta.chipbox.ui.song

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface SongListView : BaseView {
    fun setSongs(songs: ArrayList<Track>)

    fun setGames(games: HashMap<Long, Game>)

    fun refreshList()

    fun setActivityTitle(name: String)
}