package net.sigmabeta.chipbox.ui.main

import net.sigmabeta.chipbox.ui.BaseView

interface MainView : BaseView {
    fun setTrackTitle(title: String, animate: Boolean)

    fun setArtist(artist: String, animate: Boolean)

    fun setGameBoxArt(imagePath: String?, fade: Boolean)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying()

    fun hideNowPlaying()

    fun launchPlayerActivity()

    fun launchSettingsActivity()

    fun launchOnboarding()

    fun showScanning(type: Int?, name: String?)

    fun hideScanning()

    fun showFileScanError(reason: String)

    fun showFileScanSuccess(newTracks: Int)

    fun startScanner()
}
