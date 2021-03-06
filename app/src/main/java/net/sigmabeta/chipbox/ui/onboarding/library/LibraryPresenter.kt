package net.sigmabeta.chipbox.ui.onboarding.library

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import javax.inject.Inject


@ActivityScoped
class LibraryPresenter @Inject constructor() : FragmentPresenter<LibraryView>() {
    /**
     * Public Methods
     */



    /**
     * FragmentPresenter
     */



    override fun onClick(id: Int) {
        when (id) {
            R.id.button_next -> view?.onNextClicked()
            R.id.button_skip -> view?.onSkipClicked()
        }
    }

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
        state = UiState.READY
    }

    override fun teardown() = Unit

    override fun showReadyState() {
        view?.updateCurrentScreen()
    }

    /**
     * Private Methods
     */


}