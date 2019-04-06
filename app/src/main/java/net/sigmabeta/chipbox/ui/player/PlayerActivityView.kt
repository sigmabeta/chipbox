package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerActivityView : BaseView, PlayerControlsView, PlayerFragmentView {
    fun onPlaylistFabClicked()

    fun callFinish()

    fun showPlaylistScreen()

    fun hideStatusBar()
}
