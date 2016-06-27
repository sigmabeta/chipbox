package net.sigmabeta.chipbox.ui.onboarding.library

import net.sigmabeta.chipbox.ui.BaseView


interface LibraryView : BaseView {
    fun onNextClicked()

    fun onSkipClicked()

    fun onAddClicked()

    fun updateCurrentScreen()
}