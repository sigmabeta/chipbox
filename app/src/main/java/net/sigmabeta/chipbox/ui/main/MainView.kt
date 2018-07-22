package net.sigmabeta.chipbox.ui.main

import net.sigmabeta.chipbox.ui.ChromeView

interface MainView : ChromeView {
    fun launchFirstOnboarding()
    fun finish()
}
