package net.sigmabeta.chipbox.view.activity

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.view.MenuItem
import kotlinx.android.synthetic.activity_main.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.MainPresenter
import net.sigmabeta.chipbox.view.adapter.MainTabPagerAdapter
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer
import net.sigmabeta.chipbox.view.interfaces.MainView
import javax.inject.Inject

class MainActivity : BaseActivity(), MainView, FragmentContainer {
    var presenter: MainPresenter? = null
        @Inject set

    var drawerToggle: ActionBarDrawerToggle? = null

    var pagerAdapter: MainTabPagerAdapter? = null

    override fun inject() {
        ActivityInjector.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar.setDisplayHomeAsUpEnabled(true)

        setUpNavigationDrawer()
        setUpViewPagerTabs()
    }

    private fun setUpNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                layout_drawer,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)

        layout_drawer.setDrawerListener(drawerToggle)

        drawer_navigation.setNavigationItemSelectedListener {
            return@setNavigationItemSelectedListener presenter?.onOptionsItemSelected(it.itemId)
                    ?: false
        }
    }

    private fun setUpViewPagerTabs() {
        pagerAdapter = MainTabPagerAdapter(supportFragmentManager, this)
        pager_categories.adapter = pagerAdapter

        tabs_categories.setupWithViewPager(pager_categories)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Restore the drawer's state.
        drawerToggle?.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        // Persist the drawer's state.
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle the case where the nav drawer icon was clicked.
        if (drawerToggle?.onOptionsItemSelected(item) ?: false) {
            return true
        }

        // If something else was clicked, handle it ourselves.
        return presenter?.onOptionsItemSelected(item.itemId)
                ?: super.onOptionsItemSelected(item)
    }

    override fun launchFileListActivity() {
        FileListActivity.launch(this)
    }

    override fun setActivityTitle(title: String) {
        setTitle(title)
    }
}
