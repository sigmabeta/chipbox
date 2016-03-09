package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.view.interfaces.BaseView

abstract class FragmentPresenter : BasePresenter() {

    fun onCreate(arguments: Bundle?, savedInstanceState: Bundle?, view: BaseView) {
        setView(view)

        if (savedInstanceState == null) {
            setup(arguments)
        } else {
            onReCreate(savedInstanceState)
        }
    }

    fun onDestroy() {
        clearView()
    }

    /**
     * Perform actions that only need to be performed on
     * subsequent creations of a Fragment; i.e. after a rotation.
     */
    abstract fun onReCreate(savedInstanceState: Bundle)
}