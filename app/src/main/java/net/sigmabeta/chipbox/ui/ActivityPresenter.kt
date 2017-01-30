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
            repository.reopen()
            setup(arguments)
        } else {
            recreated = true
            onReCreate(arguments, savedInstanceState)
        }
    }

    fun onDestroy(finishing: Boolean) {
        clearView()

        if (finishing) {
            teardown()
            repository.close()
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
     * Perform actions that only need to be performed o subsequent creations
     * of a Activity; i.e. after rotation or low-mem activity destruction. Generally,
     * check if the operations performed in setup() need to be redone, and if so, do
     * them.
     */
    abstract fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle)

    /**
     * Perform any temporary teardown operations because the View
     * is not really destroying; more likely a configuration change.
     */
    abstract fun onTempDestroy()
}
