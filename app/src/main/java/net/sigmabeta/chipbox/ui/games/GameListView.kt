package net.sigmabeta.chipbox.ui.games

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseView

interface GameListView : BaseView {
    fun setActivityTitle(titleResource: Int)

    fun setGames(games: List<Game>)

    fun launchGameActivity(id: Long)

    fun clearClickedViewHolder()
}