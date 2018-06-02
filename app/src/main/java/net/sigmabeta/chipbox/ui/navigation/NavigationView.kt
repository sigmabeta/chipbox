package net.sigmabeta.chipbox.ui.navigation

import net.sigmabeta.chipbox.ui.ChromeView

interface NavigationView : ChromeView {
    fun showFragment(fragmentTag: String, fragmentArg: String?)

    fun setTitle(title: String)
}
