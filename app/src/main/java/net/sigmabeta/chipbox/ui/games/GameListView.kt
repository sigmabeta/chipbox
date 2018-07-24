package net.sigmabeta.chipbox.ui.games

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.ListView

interface GameListView : ListView<Game, GameViewHolder> {
    fun launchGameActivity(id: String, position: Int)

    fun setTitle(platformName: String)
}