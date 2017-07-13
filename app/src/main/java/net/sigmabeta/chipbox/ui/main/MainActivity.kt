package net.sigmabeta.chipbox.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.util.Pair
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_status.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.ScanService
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.TopLevelFragment
import net.sigmabeta.chipbox.ui.debug.DebugActivity
import net.sigmabeta.chipbox.ui.onboarding.OnboardingActivity
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.settings.SettingsActivity
import net.sigmabeta.chipbox.util.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class MainActivity : BaseActivity<MainPresenter, MainView>(), MainView, FragmentContainer {
    lateinit var presenter: MainPresenter
        @Inject set

    var drawerToggle: ActionBarDrawerToggle? = null

    var pagerAdapter: MainTabPagerAdapter? = null

    private var state = STATE_UNKNOWN

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

    override fun setTitle(title: String) {
    }

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (layout_status.translationY == 0.0f && animate) {
            text_playing_song_title.changeText(title)
        } else {
            text_playing_song_title.text = title
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (layout_status.translationY == 0.0f && animate) {
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

    override fun showNowPlaying() {
        pager_categories.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_status_bar).toInt())

        relative_now_playing.visibility = View.VISIBLE
        relative_scanning.visibility = View.GONE

        when (state) {
            STATE_IDLE -> {
                state = STATE_PLAY_PAUSE
                layout_status.slideViewOnscreen()
            }
            STATE_SCANNING -> state = STATE_SCAN_PLAY_PAUSE
            STATE_PLAY_PAUSE -> Unit
            STATE_SCAN_PLAY_PAUSE -> Unit
            else -> {
                state = STATE_PLAY_PAUSE

                layout_status.translationY = 0.0f
                layout_status.visibility = View.VISIBLE
            }
        }
    }

    override fun hideNowPlaying() {
        when (state) {
            STATE_PLAY_PAUSE -> {
                state = STATE_IDLE
                hideStatusBar()
            }
            STATE_SCAN_PLAY_PAUSE -> {
                state = STATE_SCANNING

                relative_now_playing.visibility = View.GONE
                relative_scanning.visibility = View.VISIBLE
            }
            STATE_SCANNING -> Unit
            STATE_IDLE -> Unit
            else -> {
                state = STATE_IDLE

                layout_status.visibility = View.GONE
                pager_categories.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun showScanning(type: Int?, name: String?) {
        pager_categories.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_status_bar).toInt())

        when (state) {
            STATE_IDLE -> {
                state = STATE_SCANNING

                layout_status.slideViewOnscreen()
                relative_now_playing.visibility = View.GONE
                relative_scanning.visibility = View.VISIBLE
                text_scanning_status.text = getStatusString(type, name)
            }
            STATE_SCANNING -> text_scanning_status.text = getStatusString(type, name)
            STATE_SCAN_PLAY_PAUSE -> Unit
            STATE_PLAY_PAUSE -> {
                state = STATE_SCAN_PLAY_PAUSE
                progress_now_playing.visibility = VISIBLE
            }
            else -> {
                state = STATE_SCANNING

                layout_status.translationY = 0.0f
                layout_status.visibility = View.VISIBLE

                relative_now_playing.visibility = View.GONE
                relative_scanning.visibility = View.VISIBLE
            }
        }
    }

    override fun hideScanning() {
        when (state) {
            STATE_PLAY_PAUSE -> Unit
            STATE_SCANNING -> {
                state = STATE_IDLE
                hideStatusBar()
            }
            STATE_SCAN_PLAY_PAUSE -> {
                state = STATE_PLAY_PAUSE
                progress_now_playing.visibility = GONE
            }
            STATE_IDLE -> Unit
            else -> {
                state = STATE_IDLE
                progress_now_playing.visibility = GONE

                layout_status.visibility = View.GONE
                pager_categories.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun showFileScanError(reason: String) {
        showSnackbar(reason, null, 0)
    }

    override fun showFileScanSuccess(newTracks: Int, updatedTracks: Int) {
        if (newTracks > 0) {
            if (updatedTracks > 0) {
                showSnackbar(getString(R.string.scan_status_success_new_and_updated_tracks, newTracks, updatedTracks), null, 0)
            } else {
                showSnackbar(getString(R.string.scan_status_success_new_tracks, newTracks), null, 0)
            }
        } else {

            if (updatedTracks > 0) {
                showSnackbar(getString(R.string.scan_status_success_updated_tracks, updatedTracks), null, 0)
            } else {
                showSnackbar(getString(R.string.scan_status_success_no_change), null, 0)
            }
        }
    }

    override fun showLoading() = Unit

    override fun hideLoading() = Unit

    override fun launchPlayerActivity() {
        PlayerActivity.launch(this, getShareableViews())
    }

    override fun startScanner() {
        doWithPermission(Manifest.permission.READ_EXTERNAL_STORAGE) {
            val intent = Intent(this, ScanService::class.java)
            startService(intent)

            layout_drawer.closeDrawer(Gravity.START)
        }
    }

    override fun launchSettingsActivity() {
        SettingsActivity.launch(this)
    }

    override fun launchDebugActivity() {
        DebugActivity.launch(this)
    }

    override fun launchOnboarding() {
        OnboardingActivity.launch(this, TitleFragment.TAG)
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
        getTypedApplication().appComponent.inject(this)
    }

    override fun getPresenterImpl(): MainPresenter {
        return presenter
    }

    override fun configureViews() {
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setUpNavigationDrawer()
        setUpViewPagerTabs()

        layout_status.setOnClickListener { presenter.onNowPlayingClicked() }
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

    private fun hideStatusBar() {
        pager_categories.setPadding(0, 0, 0, 0)

        if (getFragment()?.isScrolledToBottom() ?: false) {
            pager_categories.translationY = -(resources.getDimension(R.dimen.height_status_bar))
            pager_categories.slideViewToProperLocation()
        }

        layout_status.slideViewOffscreen().withEndAction {
            layout_status.visibility = View.GONE
        }
    }

    private fun getFragment(): TopLevelFragment? {
        val selectedPosition = pager_categories.currentItem

        Timber.v("Selected fragment position is %d", selectedPosition)
        val adapter = pagerAdapter

        if (adapter != null) {
            val fragment = adapter.fragments[selectedPosition]

            return fragment
        } else {
            return null
        }
    }

    private fun getStatusString(type: Int?, name: String?): String? {
        return if (name != null) {
            when (type) {
                FileScanEvent.TYPE_FOLDER -> getString(R.string.scan_status_folder, name)
                FileScanEvent.TYPE_UPDATED_TRACK -> getString(R.string.scan_status_updated_track, name)
                FileScanEvent.TYPE_DELETED_TRACK -> getString(R.string.scan_status_removed_track, name)
                else -> getString(R.string.scan_status_new_track, name)
            }
        } else {
            getString(R.string.scan_status_no_track_yet)
        }
    }

    companion object {
        val STATE_UNKNOWN = -1
        val STATE_IDLE = 0
        val STATE_SCANNING = 1
        val STATE_PLAY_PAUSE = 2
        val STATE_SCAN_PLAY_PAUSE = 3

        fun launch(context: Context) {
            val launcher = Intent(context, MainActivity::class.java)
            context.startActivity(launcher)
        }
    }
}

