package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.ui.BasePresenter
import net.sigmabeta.chipbox.ui.BaseView

abstract class FragmentPresenter : BasePresenter() {

    fun onCreate(arguments: Bundle?, savedInstanceState: Bundle?, view: BaseView) {
        setView(view)

        if (savedInstanceState == null) {
            setup(arguments)
        } else {
            onReCreate(savedInstanceState)
        }
    }

    fun onDestroy(ending: Boolean) {
        clearView()

        if (ending) {
            teardown()
        }
    }

    /**
     * Perform actions that only need to be performed on
     * subsequent creations of a Fragment; i.e. after a rotation.
     */
    abstract fun onReCreate(savedInstanceState: Bundle)
}