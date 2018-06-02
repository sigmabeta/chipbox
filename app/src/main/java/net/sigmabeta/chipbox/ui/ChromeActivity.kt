package net.sigmabeta.chipbox.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.app.ActionBarDrawerToggle
import android.util.Pair
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_chrome.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_status.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.ScanService
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.ui.debug.DebugActivity
import net.sigmabeta.chipbox.ui.onboarding.OnboardingActivity
import net.sigmabeta.chipbox.ui.onboarding.title.TitleFragment
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.settings.SettingsActivity
import net.sigmabeta.chipbox.util.animation.changeText
import net.sigmabeta.chipbox.util.animation.slideViewOffscreen
import net.sigmabeta.chipbox.util.animation.slideViewOnscreen
import net.sigmabeta.chipbox.util.animation.slideViewToProperLocation
import net.sigmabeta.chipbox.util.loadImageLowQuality

abstract class ChromeActivity<P : ChromePresenter<V>, V : ChromeView> : BaseActivity<P, V>(), ChromeView {

    var drawerToggle: ActionBarDrawerToggle? = null


    /**
     * ChromeView
     */

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (layout_bottom_bar.translationY == 0.0f && animate) {
            text_playing_title.changeText(title)
        } else {
            text_playing_title.text = title
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (layout_bottom_bar.translationY == 0.0f && animate) {
            text_playing_subtitle.changeText(artist)
        } else {
            text_playing_subtitle.text = artist
        }
    }

    override fun setGameBoxArt(imagePath: String?, fade: Boolean) {
        if (imagePath != null) {
            image_main_small.loadImageLowQuality(imagePath, fade, false)
        } else {
            image_main_small.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, false)
        }
    }

    override fun showPauseButton() {
        button_play_pause.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play_pause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
    }

    override fun showNowPlaying() {
        getScrollingContentView()?.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_status_bar).toInt())

        relative_now_playing.visibility = View.VISIBLE
        relative_scanning.visibility = View.GONE

        when (state) {
            STATE_IDLE -> {
                state = STATE_PLAY_PAUSE
                layout_bottom_bar.slideViewOnscreen()
            }
            STATE_SCANNING -> state = STATE_SCAN_PLAY_PAUSE
            STATE_PLAY_PAUSE -> Unit
            STATE_SCAN_PLAY_PAUSE -> Unit
            else -> {
                state = STATE_PLAY_PAUSE

                layout_bottom_bar.translationY = 0.0f
                layout_bottom_bar.visibility = View.VISIBLE
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

                layout_bottom_bar.visibility = View.GONE
                getScrollingContentView()?.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun showScanning(type: Int?, name: String?) {
        getScrollingContentView()?.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_status_bar).toInt())

        when (state) {
            STATE_IDLE -> {
                state = STATE_SCANNING

                layout_bottom_bar.slideViewOnscreen()
                relative_now_playing.visibility = View.GONE
                relative_scanning.visibility = View.VISIBLE
                text_scanning_status.text = getStatusString(type, name)
            }
            STATE_SCANNING -> text_scanning_status.text = getStatusString(type, name)
            STATE_SCAN_PLAY_PAUSE -> Unit
            STATE_PLAY_PAUSE -> {
                state = STATE_SCAN_PLAY_PAUSE
                progress_now_playing.visibility = View.VISIBLE
            }
            else -> {
                state = STATE_SCANNING

                layout_bottom_bar.translationY = 0.0f
                layout_bottom_bar.visibility = View.VISIBLE

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
                progress_now_playing.visibility = View.GONE
            }
            STATE_IDLE -> Unit
            else -> {
                state = STATE_IDLE
                progress_now_playing.visibility = View.GONE

                layout_bottom_bar.visibility = View.GONE
                getScrollingContentView()?.setPadding(0, 0, 0, 0)
            }
        }
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

    override fun launchPlayerActivity() {
        val shareableImageView = Pair(image_main_small as View, "image_playing_boxart")
        val shareableTitleView = Pair(text_playing_title as View, "text_playing_title")
        val shareableSubtitleView = Pair(text_playing_subtitle as View, "text_playing_subtitle")
        val shareableButtonView = Pair(button_play_pause as View, "button_play_pause")
        val shareableBackgroundView = Pair(layout_bottom_bar as View, "background")

        PlayerActivity.launch(this,
                getShareableNavBar(),
                getShareableStatusBar(),
                shareableImageView,
                shareableTitleView,
                shareableSubtitleView,
                shareableButtonView,
                shareableBackgroundView)
    }

    /**
     * Activity
     */

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
        if (drawerToggle?.onOptionsItemSelected(item) == true) {
            return true
        }

        // If something else was clicked, handle it ourselves.
        return getPresenterImpl().onOptionsItemSelected(item.itemId)
    }

    override fun inflateContent() {
        val inflater = LayoutInflater.from(this)
        val contentLayoutId = getContentLayoutId()

        val content = inflater.inflate(contentLayoutId, null)
        frame_container.addView(content, 0)
    }

    override fun getLayoutId() = R.layout.activity_chrome

    @CallSuper
    override fun configureViews() {
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setUpNavigationDrawer()

        layout_bottom_bar.setOnClickListener { getPresenterImpl().onNowPlayingClicked() }
        button_play_pause.setOnClickListener { getPresenterImpl().onPlayFabClicked() }
    }

    /**
     * Implementation Details
     */

    protected open fun shouldShowBackButton() = true

    private var state = STATE_UNKNOWN

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

    private fun hideStatusBar() {
        val scrollingContent = getScrollingContentView()
        scrollingContent?.setPadding(0, 0, 0, 0)

        if (isScrolledToBottom() ?: false) {
            scrollingContent?.translationY = -(resources.getDimension(R.dimen.height_status_bar))
            scrollingContent?.slideViewToProperLocation()
        }

        layout_bottom_bar.slideViewOffscreen().withEndAction {
            layout_bottom_bar.visibility = View.GONE
        }
    }

    private fun setUpNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                layout_drawer,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)

        if (shouldShowBackButton()) showBackArrowInToolbar()

        layout_drawer.addDrawerListener(drawerToggle!!)

        drawer_navigation.setNavigationItemSelectedListener {
            return@setNavigationItemSelectedListener getPresenterImpl().onOptionsItemSelected(it.itemId)
                    ?: false
        }
    }

    private fun showBackArrowInToolbar() {
        toolbar.setNavigationOnClickListener { v -> onBackPressed() }
        drawerToggle?.isDrawerIndicatorEnabled = false
    }

    abstract fun getContentLayoutId(): Int

    abstract fun getScrollingContentView(): View?

    abstract fun isScrolledToBottom(): Boolean

    companion object {
        const val STATE_UNKNOWN = -1
        const val STATE_IDLE = 0
        const val STATE_SCANNING = 1
        const val STATE_PLAY_PAUSE = 2
        const val STATE_SCAN_PLAY_PAUSE = 3
    }
}