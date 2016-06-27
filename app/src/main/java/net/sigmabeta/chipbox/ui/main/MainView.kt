package net.sigmabeta.chipbox.ui.main

import net.sigmabeta.chipbox.ui.BaseView

interface MainView : BaseView {
    fun launchFileListActivity()

    fun setTrackTitle(title: String, animate: Boolean)

    fun setArtist(artist: String, animate: Boolean)

    fun setGameBoxArt(imagePath: String?, fade: Boolean)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying(animate: Boolean)

    fun hideNowPlaying(animate: Boolean)

    fun launchPlayerActivity()

    fun launchScanActivity()

    fun launchSettingsActivity()

    fun launchOnboarding()
}
