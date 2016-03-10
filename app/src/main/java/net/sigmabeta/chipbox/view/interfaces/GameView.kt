package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track

interface GameView {
    fun setCursor(cursor: Cursor)

    fun setPlayingTrack(track: Track)

    fun setPlaybackState(state: Int)

    fun setGame(game: Game)
}