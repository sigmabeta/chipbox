package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import java.util.*

interface GameView : BaseView {
    fun setSongs(songs: ArrayList<Track>)

    fun setPlayingTrack(track: Track)

    fun setPlaybackState(state: Int)

    fun setGame(game: Game)
}