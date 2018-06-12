package net.sigmabeta.chipbox.ui.game

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.ChromeView

interface GameView : ChromeView {
    fun setTracks(tracks: List<Track>)

    fun setPlayingTrack(track: Track)

    fun setGame(game: Game, width: Int, height: Int)
}