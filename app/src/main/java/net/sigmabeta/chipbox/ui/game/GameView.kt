package net.sigmabeta.chipbox.ui.game

import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface GameView : BaseView {
    fun setTracks(tracks: MutableList<Track>)

    fun setPlayingTrack(track: Track)

    fun setGame(game: Game?)
}