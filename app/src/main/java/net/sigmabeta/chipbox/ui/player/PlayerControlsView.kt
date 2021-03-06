package net.sigmabeta.chipbox.ui.player

import net.sigmabeta.chipbox.ui.BaseView

interface PlayerControlsView : BaseView {
    fun showPauseButton()

    fun showPlayButton()

    fun setShuffleEnabled()

    fun setShuffleDisabled()

    fun setRepeatDisabled()

    fun setRepeatAll()

    fun setRepeatOne()

    fun setRepeatInfinite()

    fun finish()
}