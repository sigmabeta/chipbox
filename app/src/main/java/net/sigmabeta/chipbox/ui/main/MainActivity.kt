package net.sigmabeta.chipbox.ui.main

import android.content.Context
import android.content.Intent
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ChromeActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.ListFragment
import timber.log.Timber
import javax.inject.Inject

class MainActivity : ChromeActivity<MainPresenter, MainView>(), MainView, FragmentContainer {
    lateinit var presenter: MainPresenter
        @Inject set

    var pagerAdapter: MainTabPagerAdapter? = null

    override fun setTitle(title: String) = Unit

    /**
     * ChromeActivity
     */

    override fun getScrollingContentView() = pager_categories

    override fun isScrolledToBottom(): Boolean = getFragment()?.isScrolledToBottom() ?: false

    /**
     * BaseView
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun getPresenterImpl(): MainPresenter {
        return presenter
    }

    /**
     * BaseActivity
     */

    override fun configureViews() {
        super.configureViews()
        setUpViewPagerTabs()
    }

    override fun getContentLayoutId() = R.layout.activity_main

    override fun getContentLayout() = coordinator_main

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    /**
     * Implementation Details
     */

    private fun getFragment(): ListFragment<*,*,*,*,*>? {
        val selectedPosition = pager_categories.currentItem

        Timber.v("Selected fragment position is %d", selectedPosition)
        val adapter = pagerAdapter

        return if (adapter != null) {
            adapter.fragments[selectedPosition]
        } else {
            null
        }
    }

    private fun setUpViewPagerTabs() {
        pagerAdapter = MainTabPagerAdapter(supportFragmentManager, this)
        pager_categories.adapter = pagerAdapter

        tabs_categories.setupWithViewPager(pager_categories)
    }

    companion object {
        fun launch(context: Context) {
            val launcher = Intent(context, MainActivity::class.java)
            context.startActivity(launcher)
        }
    }
}

