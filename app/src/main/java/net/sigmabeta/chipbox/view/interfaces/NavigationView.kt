package net.sigmabeta.chipbox.view.interfaces

interface NavigationView {
    fun showFragment(fragmentTag: String, fragmentArg: Long)

    fun setTrackTitle(title: String)

    fun setArtist(artist: String)

    fun setGameBoxart(gameId: Long)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying(animate: Boolean)

    fun hideNowPlaying(animate: Boolean)

    fun launchPlayerActivity()
}
