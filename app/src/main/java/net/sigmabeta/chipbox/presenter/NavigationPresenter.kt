package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.view.interfaces.NavigationView
import javax.inject.Inject


class NavigationPresenter @Inject constructor(val view: NavigationView) {

    fun onCreate(fragmentTag: String, fragmentArg: Long, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            view.showFragment(fragmentTag, fragmentArg)
        }
    }
}
