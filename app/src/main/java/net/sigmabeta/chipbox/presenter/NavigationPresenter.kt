package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.view.interfaces.NavigationView
import javax.inject.Inject


class NavigationPresenter @Inject constructor(val view: NavigationView) {

    fun onCreate(fragmentTag: String, fragmentArg: Long) {
        view.showFragment(fragmentTag, fragmentArg)
    }
}
