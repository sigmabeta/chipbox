package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.view.interfaces.MainView
import javax.inject.Inject

class MainPresenter @Inject constructor(val view: MainView) {

    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.drawer_add_folder -> view.launchFileListActivity()
        }

        return true
    }
}
