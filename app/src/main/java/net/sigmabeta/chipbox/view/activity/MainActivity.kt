package net.sigmabeta.chipbox.view.activity

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.view.MenuItem
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_now_playing.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.MainPresenter
import net.sigmabeta.chipbox.util.slideViewDown
import net.sigmabeta.chipbox.util.slideViewUp
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
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setUpNavigationDrawer()
        setUpViewPagerTabs()

        layout_now_playing.setOnClickListener { presenter?.onNowPlayingClicked() }
        fab_play_pause.setOnClickListener { presenter?.onPlayFabClicked() }
    }

    override fun onResume() {
        super.onResume()
        presenter?.onResume()
    }

    override fun onPause() {
        super.onPause()
        presenter?.onPause()
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

    override fun setTrackTitle(title: String) {
        text_playing_song_title.text = title
    }

    override fun setArtist(artist: String) {
        text_playing_song_artist.text = artist
    }

    override fun setGameBoxart(gameId: Long) {
        val imagesFolderPath = "file://" + getExternalFilesDir(null).absolutePath + "/images/"
        val imagePath = imagesFolderPath + gameId.toString() + "/local.png"

        Picasso.with(this)
                .load(imagePath)
                .centerCrop()
                .fit()
                .into(image_playing_game_box_art)
    }


    override fun showPauseButton() {
        fab_play_pause.setImageResource(R.drawable.ic_pause_white_48dp)
    }

    override fun showPlayButton() {
        fab_play_pause.setImageResource(R.drawable.ic_play_48dp)
    }

    override fun showNowPlaying(animate: Boolean) {
        coordinator_main.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_now_playing).toInt())

        if (animate) {
            layout_now_playing.slideViewUp()
        }
    }

    override fun hideNowPlaying(animate: Boolean) {
        coordinator_main.setPadding(0, 0, 0, 0)

        if (animate) {
            layout_now_playing.slideViewDown().withEndAction {
                layout_now_playing.visibility = View.GONE
            }
        } else {
            layout_now_playing.visibility = View.GONE
        }
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(this)
    }
}
