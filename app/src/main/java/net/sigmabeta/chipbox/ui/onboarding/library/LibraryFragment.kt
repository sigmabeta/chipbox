package net.sigmabeta.chipbox.ui.onboarding.library

import kotlinx.android.synthetic.main.fragment_library.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.onboarding.OnboardingView
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class LibraryFragment : BaseFragment<LibraryPresenter, LibraryView>(), LibraryView {
    lateinit var presenter: LibraryPresenter
        @Inject set

    /**
     * LibraryView
     */

    override fun onNextClicked() {
        (activity as OnboardingView).showNextScreen()
    }

    override fun onSkipClicked() {
        (activity as OnboardingView).skip()
    }

    override fun updateCurrentScreen() {
        (activity as OnboardingView).updateCurrentScreen(TAG)
    }

    /**
     * BaseFragment
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject(): Boolean {
        val container = activity
        if (container is BaseActivity<*, *>) {container.getFragmentComponent()?.let {
                it.inject(this)
                return true
            } ?: let {
                Timber.e("${className()} injection failure: ${container?.className()}'s FragmentComponent not valid.")
                return false
            }
        } else {
            Timber.e("${className()} injection failure: ${container?.className()} not valid.")
            return false
        }
    }

    override fun getPresenterImpl(): LibraryPresenter = presenter

    override fun getLayoutId() = R.layout.fragment_library

    override fun getContentLayout() = layout_content

    override fun getSharedImage() = null

    override fun configureViews() {
        button_next.setOnClickListener(this)
        button_skip.setOnClickListener(this)
    }

    override fun getFragmentTag(): String = TAG

    companion object {
        val TAG = "${BuildConfig.APPLICATION_ID}.library"

        fun newInstance() = LibraryFragment()
    }
}