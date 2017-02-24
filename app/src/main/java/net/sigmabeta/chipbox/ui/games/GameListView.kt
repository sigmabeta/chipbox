package net.sigmabeta.chipbox.ui.games

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseView

interface GameListView : BaseView {
    fun setTitle(platformName: String)

    fun setGames(games: List<Game>)

    fun launchGameActivity(id: String)

    fun startRescan()

    fun clearClickedViewHolder()

    fun showContent()

    fun showEmptyState()
}