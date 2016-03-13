package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import java.util.*

interface SongListView : BaseView {
    fun setSongs(songs: ArrayList<Track>)

    fun setGames(games: HashMap<Long, Game>)

    fun launchPlayerActivity()

    fun refreshList()
}