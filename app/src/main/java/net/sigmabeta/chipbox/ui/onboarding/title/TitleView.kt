package net.sigmabeta.chipbox.ui.onboarding.title

import net.sigmabeta.chipbox.ui.BaseView


interface TitleView : BaseView {
    fun onNextClicked()

    fun onSkipClicked()

    fun updateCurrentScreen()
}