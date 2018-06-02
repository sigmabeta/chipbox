package net.sigmabeta.chipbox.ui

interface ChromeView : BaseView {
    fun setTrackTitle(title: String, animate: Boolean)

    fun setArtist(artist: String, animate: Boolean)

    fun setGameBoxArt(imagePath: String?, fade: Boolean)

    fun showPauseButton()

    fun showPlayButton()

    fun showNowPlaying()

    fun hideNowPlaying()

    fun launchPlayerActivity()

    fun launchSettingsActivity()

    fun launchDebugActivity()

    fun launchOnboarding()

    fun showScanning(type: Int?, name: String?)

    fun hideScanning()

    fun showFileScanSuccess(newTracks: Int, updatedTracks: Int)

    fun startScanner()
}