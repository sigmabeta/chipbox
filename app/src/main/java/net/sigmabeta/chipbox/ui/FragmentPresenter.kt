package net.sigmabeta.chipbox.ui

import android.os.Bundle

abstract class FragmentPresenter : BasePresenter() {
    fun onCreate(arguments: Bundle?, savedInstanceState: Bundle?, view: BaseView) {
        setView(view)

        if (savedInstanceState == null) {
            setup(arguments)
        } else {
            onReCreate(arguments, savedInstanceState)
        }
    }

    fun onDestroy(ending: Boolean) {
        clearView()

        if (ending) {
            teardown()
        }
    }

    /**
     * Perform actions that only need to be performed o subsequent creations
     * of a Fragment; i.e. after rotation or low-mem activity destruction. Generally,
     * check if the operations performed in setup() need to be redone, and if so, do
     * them.
     */
    abstract fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle)
}