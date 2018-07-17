package net.sigmabeta.chipbox.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import kotlinx.android.synthetic.main.activity_onboarding.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.onboarding.library.LibraryFragment
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import javax.inject.Inject

class OnboardingActivity : BaseActivity<OnboardingPresenter, OnboardingView>(), OnboardingView {

    override fun getPresenterImpl() = presenter

    override fun getSharedImage() = null

    override fun shouldDelayTransitionForFragment() = false

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

    override fun exit(andLaunchMain: Boolean) {
        val result = if (andLaunchMain) {
            Activity.RESULT_OK
        } else {
            Activity.RESULT_CANCELED
        }

        setResult(result)
        finish()
    }

    override fun updateCurrentScreen(tag: String) {
        presenter.currentTag = tag
    }

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun configureViews() {
        setContentView(getLayoutId())
    }

    override fun getLayoutId() = R.layout.activity_onboarding

    override fun getContentLayout() = frame_content

    override fun onBackPressed() {
        // From superclass
        val fragmentManager = supportFragmentManager
        val isStateSaved = fragmentManager.isStateSaved
        if (isStateSaved && Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            return
        }
        if (isStateSaved || !fragmentManager.popBackStackImmediate()) {
            presenter.onBackPressed()
        }
    }

    override fun onClick(clicked: View) {
        presenter.onClick(clicked.id)
    }

    private fun showFragment(fragment: BaseFragment<*, *>, backstack: Boolean) {
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

        val REQUEST_ONBOARDING = 1234

        val ARGUMENT_PAGE_TAG = "${ACTIVITY_TAG}.page.tag"

        fun launchForResult(activity: Activity) {
            val launcher = Intent(activity, OnboardingActivity::class.java)
            activity.startActivityForResult(launcher, REQUEST_ONBOARDING)
        }

        fun launchNoResult(context: Context, tag: String) {
            val launcher = Intent(context, OnboardingActivity::class.java)

            launcher.putExtra(ARGUMENT_PAGE_TAG, tag)

            context.startActivity(launcher)
        }
    }
}
