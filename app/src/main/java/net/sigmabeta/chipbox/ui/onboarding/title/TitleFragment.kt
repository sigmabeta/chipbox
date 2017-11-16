package net.sigmabeta.chipbox.ui.onboarding.title

import kotlinx.android.synthetic.main.fragment_title.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.onboarding.OnboardingView
import javax.inject.Inject

@ActivityScoped
class TitleFragment : BaseFragment<TitlePresenter, TitleView>(), TitleView {
    lateinit var presenter: TitlePresenter
        @Inject set

    /**
     * TitleView
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

    override fun inject() {
        val container = activity
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this)
        }
    }

    override fun getPresenterImpl(): TitlePresenter = presenter

    override fun getLayoutId() = R.layout.fragment_title

    override fun getContentLayout() = layout_content

    override fun getSharedImage() = null

    override fun configureViews() {
        button_next.setOnClickListener(this)
        button_skip.setOnClickListener(this)
    }

    override fun getFragmentTag(): String = TAG

    companion object {
        val TAG = "${BuildConfig.APPLICATION_ID}.title"

        fun newInstance() = TitleFragment()
    }
}