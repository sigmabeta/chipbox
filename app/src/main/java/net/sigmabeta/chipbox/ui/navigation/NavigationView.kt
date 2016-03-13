package net.sigmabeta.chipbox.ui.navigation

import net.sigmabeta.chipbox.ui.BaseView

interface NavigationView : BaseView {
    fun showFragment(fragmentTag: String, fragmentArg: Long)

    fun setTrackTitle(title: String)

    fun setArtist(artist: String)

    fun setGameBoxart(gameId: Long)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying(animate: Boolean)

    fun hideNowPlaying(animate: Boolean)

    fun launchPlayerActivity()

    fun setTitle(title: String)
}
