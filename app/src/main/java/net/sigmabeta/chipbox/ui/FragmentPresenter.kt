package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.util.logWarning

abstract class FragmentPresenter<V : BaseView> : BasePresenter<V>() {
    fun onCreate(arguments: Bundle?, savedInstanceState: Bundle?, view: V) {
        this.view = view

        if (savedInstanceState == null) {
            repository.reopen()
            setupStartTime = System.currentTimeMillis()

            setup(arguments)
        } else {
            onReCreate(arguments, savedInstanceState)
        }
    }

    fun onDestroy(ending: Boolean, destroyedView: V) {
        if (destroyedView === this.view) {
            clearView()

            if (ending) {
                setupStartTime = -1
                teardown()

                needsSetup = true
                loading = false
            }
        } else if (this.view == null) {
            logWarning("Cannot clear reference; Presenter has already cleared reference.")
        } else {
            handleError(InvalidClearViewException(view), null)
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