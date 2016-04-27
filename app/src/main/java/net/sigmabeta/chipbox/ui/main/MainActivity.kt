package net.sigmabeta.chipbox.ui.main

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.util.Pair
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_now_playing.*
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.TopLevelFragment
import net.sigmabeta.chipbox.ui.file.FilesActivity
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.scan.ScanActivity
import net.sigmabeta.chipbox.ui.settings.SettingsActivity
import net.sigmabeta.chipbox.util.*
import java.util.*
import javax.inject.Inject

class MainActivity : BaseActivity(), MainView, FragmentContainer {
    lateinit var presenter: MainPresenter
        @Inject set

    var drawerToggle: ActionBarDrawerToggle? = null

    var pagerAdapter: MainTabPagerAdapter? = null

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
        return presenter.onOptionsItemSelected(item.itemId)
    }

    override fun launchFileListActivity() {
        FilesActivity.launch(this)
    }

    override fun setTitle(title: String) {
    }

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (layout_now_playing.translationY == 0.0f && animate) {
            text_playing_song_title.changeText(title)
        } else {
            text_playing_song_title.text = title
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (layout_now_playing.translationY == 0.0f && animate) {
            text_playing_song_artist.changeText(artist)
        } else {
            text_playing_song_artist.text = artist
        }
    }

    override fun setGameBoxArt(imagePath: String?, fade: Boolean) {
        if (imagePath != null) {
            image_playing_game_box_art.loadImageLowQuality(imagePath, fade, false)
        } else {
            image_playing_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, false)
        }
    }

    override fun showPauseButton() {
        button_play_pause.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play_pause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
    }

    override fun showNowPlaying(animate: Boolean) {
        pager_categories.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_now_playing).toInt())

        if (animate) {
            layout_now_playing.slideViewOnscreen()
        } else {
            layout_now_playing.translationY = 0.0f
            layout_now_playing.visibility = View.VISIBLE
        }
    }

    override fun hideNowPlaying(animate: Boolean) {
        pager_categories.setPadding(0, 0, 0, 0)

        if (animate) {
            if (getFragment()?.isScrolledToBottom() ?: false) {
                pager_categories.translationY = -(resources.getDimension(R.dimen.height_now_playing))
                pager_categories.slideViewToProperLocation()
            }

            layout_now_playing.slideViewOffscreen().withEndAction {
                layout_now_playing.visibility = View.GONE
            }
        } else {
            layout_now_playing.visibility = View.GONE
        }
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(this, getShareableViews())
    }

    override fun launchScanActivity() {
        ScanActivity.launch(this)
    }

    override fun launchSettingsActivity() {
        SettingsActivity.launch(this)
    }

    override fun getShareableViews(): Array<Pair<View, String>>? {
        val views = ArrayList<Pair<View, String>>(3)

        views.add(Pair(image_playing_game_box_art as View, "image_playing_boxart"))
        views.add(Pair(button_play_pause as View, "button_play_pause"))

        getShareableNavBar()?.let {
            views.add(it)
        }

        return views.toTypedArray()
    }

    override fun inject() {
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setUpNavigationDrawer()
        setUpViewPagerTabs()

        layout_now_playing.setOnClickListener { presenter.onNowPlayingClicked() }
        button_play_pause.setOnClickListener { presenter.onPlayFabClicked() }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_main
    }

    override fun getContentLayout(): FrameLayout {
        return findViewById(android.R.id.content) as FrameLayout
    }

    private fun setUpNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                layout_drawer,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)

        layout_drawer.setDrawerListener(drawerToggle)

        drawer_navigation.setNavigationItemSelectedListener {
            return@setNavigationItemSelectedListener presenter.onOptionsItemSelected(it.itemId)
                    ?: false
        }
    }

    private fun setUpViewPagerTabs() {
        pagerAdapter = MainTabPagerAdapter(supportFragmentManager, this)
        pager_categories.adapter = pagerAdapter

        tabs_categories.setupWithViewPager(pager_categories)
    }

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    private fun getFragment(): TopLevelFragment? {
        val selectedPosition = pager_categories.currentItem

        logVerbose("[MainActivity] Selected fragment position is $selectedPosition")
        val adapter = pagerAdapter

        if (adapter != null) {
            val fragment = adapter.fragments[selectedPosition]

            return fragment
        } else {
            return null
        }
    }
}

