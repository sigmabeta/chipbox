package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerActivityView : BaseView {
    fun showPlayerFragment()

    fun showControlsFragment()
}
