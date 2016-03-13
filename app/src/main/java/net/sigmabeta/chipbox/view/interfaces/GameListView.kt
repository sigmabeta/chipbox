package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Game
import java.util.*

interface GameListView : BaseView {
    fun setGames(games: ArrayList<Game>)

    fun launchGameActivity(id: Long)
}