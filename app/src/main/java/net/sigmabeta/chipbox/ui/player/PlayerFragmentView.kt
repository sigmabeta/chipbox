package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerFragmentView : BaseView {
    fun setTrackTitle(title: String, animate: Boolean)

    fun setGameTitle(title: String, animate: Boolean)

    fun setArtist(artist: String, animate: Boolean)

    fun setTimeElapsed(time: String)

    fun setGameBoxArt(path: String?, fade: Boolean)

    fun setTrackLength(trackLength: String, animate: Boolean)

    fun setSeekProgress(percentPlayed: Int)
}