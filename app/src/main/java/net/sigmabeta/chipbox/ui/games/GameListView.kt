package net.sigmabeta.chipbox.ui.games

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface GameListView : BaseView {
    fun setActivityTitle(titleResource: Int)

    fun setGames(games: ArrayList<Game>)

    fun launchGameActivity(id: Long)

    fun clearClickedViewHolder()
}