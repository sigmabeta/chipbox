package net.sigmabeta.chipbox.ui.game

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface GameView : BaseView {
    fun setSongs(songs: ArrayList<Track>)

    fun setPlayingTrack(track: Track)

    fun setPlaybackState(state: Int)

    fun setGame(game: Game)
}