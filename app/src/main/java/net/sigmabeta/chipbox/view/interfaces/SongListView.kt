package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import java.util.*

interface SongListView {
    fun onItemClick(track: Track, position: Int)

    fun setCursor(cursor: Cursor)

    fun setGames(games: HashMap<Long, Game>)

    fun launchPlayerActivity()

    fun refreshList()
}