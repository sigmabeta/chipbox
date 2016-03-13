package net.sigmabeta.chipbox.ui.games

import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface GameListView : BaseView {
    fun setGames(games: ArrayList<Game>)

    fun launchGameActivity(id: Long)
}