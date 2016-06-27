package net.sigmabeta.chipbox.ui.onboarding

import android.os.Bundle
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.onboarding.library.LibraryFragment
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPresenter @Inject constructor() : ActivityPresenter() {
    var view: OnboardingView? = null

    var currentTag: String? = null

    var launchedWithTag: Boolean = true

    /**
     * Public Methods
     */

    fun showNextScreen() {
        when (currentTag) {
            TitleFragment.TAG -> view?.showLibraryPage()
            LibraryFragment.TAG -> view?.exit(!launchedWithTag)
        }
    }

    fun skip() {
        view?.exit(!launchedWithTag)
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
        // TODO Check if user's already been onboarded.

        val tag = arguments?.getString(OnboardingActivity.ARGUMENT_PAGE_TAG)

        when (tag) {
            TitleFragment.TAG -> view?.showTitlePage()
            else -> {
                launchedWithTag = false
                view?.showTitlePage()
            }
        }
    }

    override fun teardown() {
        launchedWithTag = true
        currentTag = null
    }

    override fun updateViewState() = Unit

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is OnboardingView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */
}