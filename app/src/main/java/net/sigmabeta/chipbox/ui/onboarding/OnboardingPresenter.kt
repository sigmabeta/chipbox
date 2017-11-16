package net.sigmabeta.chipbox.ui.onboarding

import android.os.Bundle
import net.sigmabeta.chipbox.backend.PrefManager
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import net.sigmabeta.chipbox.ui.onboarding.library.LibraryFragment
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPresenter @Inject constructor(val prefManager: PrefManager) : ActivityPresenter<OnboardingView>() {
    var currentTag: String? = null

    var launchedWithTag: Boolean = true

    /**
     * Public Methods
     */

    fun showNextScreen() {
        when (currentTag) {
            TitleFragment.TAG -> view?.showLibraryPage()
            LibraryFragment.TAG -> exitHelper()
        }
    }

    fun skip() {
        exitHelper()
    }

    /**
     * ActivityPresenter
     */

    override fun onReenter() = Unit

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun onClick(id: Int) = Unit

    override fun setup(arguments: Bundle?) {
        state = UiState.READY

        val tag = arguments?.getString(OnboardingActivity.ARGUMENT_PAGE_TAG)

        when (tag) {
            TitleFragment.TAG -> view?.showTitlePage()
            else -> {
                launchedWithTag = false
                if (prefManager.get(PrefManager.KEY_ONBOARDED)) {
                    exitHelper()
                    return
                } else {
                    view?.showTitlePage()
                }
            }
        }

        view?.configureViews()
    }

    override fun teardown() {
        launchedWithTag = true
        currentTag = null
    }

    override fun showReadyState() = Unit

    /**
     * Private Methods
     */

    private fun exitHelper() {
        prefManager.set(PrefManager.KEY_ONBOARDED, true)
        view?.exit(!launchedWithTag)
    }
}