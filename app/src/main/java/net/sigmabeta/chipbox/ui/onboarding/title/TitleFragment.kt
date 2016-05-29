package net.sigmabeta.chipbox.ui.onboarding.title

import kotlinx.android.synthetic.main.fragment_title.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject

@ActivityScoped
class TitleFragment : BaseFragment(), TitleView {
    lateinit var presenter: TitlePresenter
        @Inject set

    /**
     * TitleView
     */


    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

    override fun getPresenter(): FragmentPresenter = presenter

    override fun getLayoutId() = R.layout.fragment_title

    override fun getContentLayout() = layout_content

    override fun getSharedImage() = null

    override fun configureViews() = Unit

    override fun getFragmentTag(): String = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.title"

        fun newInstance() = TitleFragment()
    }
}