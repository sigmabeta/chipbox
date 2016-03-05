package net.sigmabeta.chipbox.view.interfaces

interface PlayerFragmentView {
    fun setTrackTitle(title: String)

    fun setGameTitle(title: String)

    fun setArtist(artist: String)

    fun setTimeElapsed(time: String)

    fun setGameBoxart(gameId: Long)

    fun showPauseButton()

    fun showPlayButton()

    fun setTrackLength(trackLength: String)

    fun setUnderrunCount(count: String)

    fun setProgress(percentPlayed: Int)
}