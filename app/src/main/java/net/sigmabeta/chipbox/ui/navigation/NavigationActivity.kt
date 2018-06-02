package net.sigmabeta.chipbox.ui.navigation

import android.content.Context
import android.content.Intent
import android.view.View
import kotlinx.android.synthetic.main.activity_navigation.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.ChromeActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.NavigationFragment
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.track.TrackListFragment
import javax.inject.Inject

class NavigationActivity : ChromeActivity<NavigationPresenter, NavigationView>(), NavigationView, FragmentContainer {
    lateinit var presenter: NavigationPresenter
        @Inject set

    /**
     * NavigationView
     */

    override fun setTitle(title: String) {
        this.title = title
    }

    override fun showFragment(fragmentTag: String, fragmentArg: String?) {
        var fragment: BaseFragment<*, *>

        when (fragmentTag) {
            GameGridFragment.FRAGMENT_TAG -> fragment = GameGridFragment.newInstance(fragmentArg)
            TrackListFragment.FRAGMENT_TAG -> fragment = TrackListFragment.newInstance(fragmentArg)
            else -> {
                presenter.onUnsupportedFragment()
                return
            }
        }
        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, fragmentTag)
                .commit()
    }

    /**
     * BaseView
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun getPresenterImpl() = presenter

    /**
     * ChromeActivity
     */

    override fun getScrollingContentView() = getFragment()?.getScrollingView()

    override fun isScrolledToBottom(): Boolean = getFragment()?.isScrolledToBottom() ?: false

    /**
     * BaseActivity
     */

    override fun getContentLayoutId() = R.layout.activity_navigation

    override fun getContentLayout() = frame_fragment

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    /**
     * Implementation Details
     */

    private fun getFragment(): NavigationFragment? {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_fragment) as NavigationFragment?
        return fragment
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.navigation"

        val ARGUMENT_FRAGMENT_TAG = "${ACTIVITY_TAG}.fragment_tag"
        val ARGUMENT_FRAGMENT_ARG_STRING = "${ACTIVITY_TAG}.fragment_argument_string"

        fun launch(context: Context, fragmentTag: String, fragmentArg: String) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)
            launcher.putExtra(ARGUMENT_FRAGMENT_ARG_STRING, fragmentArg)

            context.startActivity(launcher)
        }
    }
}