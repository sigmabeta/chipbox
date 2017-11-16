package net.sigmabeta.chipbox.ui.onboarding.title

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import javax.inject.Inject


@ActivityScoped
class TitlePresenter @Inject constructor() : FragmentPresenter<TitleView>() {
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