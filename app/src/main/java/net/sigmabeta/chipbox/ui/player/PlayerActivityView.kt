package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerActivityView : BaseView {
    fun showPlayerFragment()

    fun showControlsFragment()

    fun onPlaylistFabClicked()

    fun showPlaylistFragment()

    fun hidePlaylistFragment()

    fun callFinish()

    fun showStatusBar()

    fun hideStatusBar()
}
