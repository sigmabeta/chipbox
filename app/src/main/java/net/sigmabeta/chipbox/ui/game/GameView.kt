package net.sigmabeta.chipbox.ui.game

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface GameView : BaseView {
    fun setSongs(songs: List<Track>)

    fun setPlayingTrack(track: Track)

    fun setPlaybackState(state: Int)

    fun setGame(game: Game?)
}