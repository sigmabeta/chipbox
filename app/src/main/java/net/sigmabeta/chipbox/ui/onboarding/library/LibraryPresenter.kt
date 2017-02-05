package net.sigmabeta.chipbox.ui.onboarding.library

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject


@ActivityScoped
class LibraryPresenter @Inject constructor() : FragmentPresenter<LibraryView>() {
    /**
     * Public Methods
     */



    /**
     * FragmentPresenter
     */

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_next -> view?.onNextClicked()
            R.id.button_skip -> view?.onSkipClicked()
            R.id.button_add -> view?.onAddClicked()
        }
    }

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
        needsSetup = false
    }

    override fun teardown() = Unit

    override fun updateViewState() {
        view?.updateCurrentScreen()
    }

    /**
     * Private Methods
     */


}