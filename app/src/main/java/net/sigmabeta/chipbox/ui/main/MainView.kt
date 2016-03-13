package net.sigmabeta.chipbox.ui.main

import net.sigmabeta.chipbox.ui.BaseView

interface MainView : BaseView {
    fun launchFileListActivity()

    fun setTrackTitle(title: String)

    fun setArtist(artist: String)

    fun setGameBoxart(gameId: Long)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying(animate: Boolean)

    fun hideNowPlaying(animate: Boolean)

    fun launchPlayerActivity()

    fun launchScanActivity()
}
