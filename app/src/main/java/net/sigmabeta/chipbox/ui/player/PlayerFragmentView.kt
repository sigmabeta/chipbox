package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerFragmentView : BaseView {
    fun setTrackTitle(title: String)

    fun setGameTitle(title: String)

    fun setArtist(artist: String)

    fun setTimeElapsed(time: String)

    fun setGameBoxArt(path: String?, fade: Boolean)

    fun setTrackLength(trackLength: String)

    fun setUnderrunCount(count: String)

    fun setProgress(percentPlayed: Int)

    fun showPlaylist()
}