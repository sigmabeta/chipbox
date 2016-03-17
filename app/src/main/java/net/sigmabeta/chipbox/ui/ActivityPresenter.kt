package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.dagger.component.FragmentComponent

abstract class ActivityPresenter : BasePresenter() {
    var fragmentComponent: FragmentComponent = ChipboxApplication.appComponent.plusFragments()
    var recreated = false

    fun onCreate(arguments: Bundle?, savedInstanceState: Bundle?, view: BaseView) {
        setView(view)

        if (savedInstanceState == null) {
            setup(arguments)
        } else {
            recreated = true
            onReCreate(savedInstanceState)
        }
    }

    fun onDestroy(finishing: Boolean) {
        clearView()

        if (finishing) {
            teardown()
            recreated = false
        } else {
            onTempDestroy()
        }
    }

    /**
     * Perform actions that need to be performed in order for a
     * re-enter animation to not screw up.
     */
    abstract fun onReenter()

    /**
     * Perform actions that only need to be performed on
     * subsequent creations of an activity; i.e. after a rotation.
     */
    abstract fun onReCreate(savedInstanceState: Bundle)

    /**
     * Perform any temporary teardown operations because the View
     * is not really destroying; more likely a configuration change.
     */
    abstract fun onTempDestroy()
}
