package net.sigmabeta.chipbox.ui.onboarding

import android.content.Context
import android.content.Intent
import kotlinx.android.synthetic.main.activity_onboarding.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.main.MainActivity
import net.sigmabeta.chipbox.ui.onboarding.library.LibraryFragment
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import javax.inject.Inject

class OnboardingActivity : BaseActivity(), OnboardingView {
    lateinit var presenter: OnboardingPresenter
        @Inject set

    /**
     * OnboardingView
     */

    override fun showNextScreen() {
        presenter.showNextScreen()
    }

    override fun skip() {
        presenter.skip()
    }

    override fun showTitlePage() {
        val fragment = TitleFragment.newInstance()
        showFragment(fragment, false)
    }

    override fun showLibraryPage() {
        val fragment = LibraryFragment.newInstance()
        showFragment(fragment, true)
    }

    override fun exit() {
        finish()
        MainActivity.launch(this)
    }

    override fun updateCurrentScreen(tag: String) {
        presenter.currentTag = tag
    }

    /**
     * BaseActivity
     */

    override fun inject() = ChipboxApplication.appComponent.inject(this)

    override fun getPresenter(): ActivityPresenter = presenter

    override fun configureViews() = Unit

    override fun getLayoutId() = R.layout.activity_onboarding

    override fun getContentLayout() = frame_content

    override fun getSharedImage() = null

    override fun shouldDelayTransitionForFragment() = false

    private fun showFragment(fragment: BaseFragment, backstack: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()

        if (backstack) {
            transaction.setCustomAnimations(R.anim.fade_in_right,
                    R.anim.fade_out_left,
                    R.anim.fade_in_left,
                    R.anim.fade_out_right)
        } else {
            transaction.setCustomAnimations(R.anim.fade_in_bottom, 0)
        }

        transaction.replace(R.id.frame_fragment, fragment, fragment.getFragmentTag())

        if (backstack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.onboarding"

        val ARGUMENT_PAGE_ID = "${ACTIVITY_TAG}.page.id"

        fun launch(context: Context, id: Int) {
            val launcher = Intent(context, OnboardingActivity::class.java)

            launcher.putExtra(ARGUMENT_PAGE_ID, id)

            context.startActivity(launcher)
        }
    }
}
