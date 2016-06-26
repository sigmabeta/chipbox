package net.sigmabeta.chipbox.ui.onboarding

import net.sigmabeta.chipbox.ui.BaseView

interface OnboardingView : BaseView {
    fun showTitlePage()

    fun showNextScreen()

    fun skip()

    fun exit()
}