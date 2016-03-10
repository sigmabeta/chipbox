package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.layout_now_playing.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.ActivityPresenter
import net.sigmabeta.chipbox.presenter.NavigationPresenter
import net.sigmabeta.chipbox.util.loadImageLowQuality
import net.sigmabeta.chipbox.util.slideViewOffscreen
import net.sigmabeta.chipbox.util.slideViewOnscreen
import net.sigmabeta.chipbox.util.slideViewToProperLocation
import net.sigmabeta.chipbox.view.fragment.BaseFragment
import net.sigmabeta.chipbox.view.fragment.GameGridFragment
import net.sigmabeta.chipbox.view.fragment.SongListFragment
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer
import net.sigmabeta.chipbox.view.interfaces.NavigationFragment
import net.sigmabeta.chipbox.view.interfaces.NavigationView
import javax.inject.Inject

class NavigationActivity : BaseActivity(), NavigationView, FragmentContainer {
    lateinit var presenter: NavigationPresenter
        @Inject set

    /**
     * NavigationView
     */

    override fun showFragment(fragmentTag: String, fragmentArg: Long) {
        var fragment: BaseFragment

        when (fragmentTag) {
            GameGridFragment.FRAGMENT_TAG -> fragment = GameGridFragment.newInstance(fragmentArg.toInt())
            SongListFragment.FRAGMENT_TAG -> fragment = SongListFragment.newInstance(fragmentArg)
            else -> {
                showToastMessage("Unsupported fragment.")
                return
            }
        }
        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, fragmentTag)
                .commit()
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

        loadImageLowQuality(image_playing_game_box_art, imagePath)
    }

    override fun showPauseButton() {
        fab_play_pause.setImageResource(R.drawable.ic_pause_white_48dp)
    }

    override fun showPlayButton() {
        fab_play_pause.setImageResource(R.drawable.ic_play_48dp)
    }

    override fun showNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, resources.getDimension(R.dimen.height_now_playing).toInt())

        if (animate) {
            layout_now_playing.slideViewOnscreen()
        }
    }

    override fun hideNowPlaying(animate: Boolean) {
        frame_fragment.setPadding(0, 0, 0, 0)

        if (animate) {
            if (getFragment()?.isScrolledToBottom() ?: false) {
                frame_fragment.translationY = -(resources.getDimension(R.dimen.height_now_playing))
                frame_fragment.slideViewToProperLocation()
            }

            layout_now_playing.slideViewOffscreen().withEndAction {
                layout_now_playing.visibility = View.GONE
            }
        } else {
            layout_now_playing.visibility = View.GONE
        }
    }

    override fun launchPlayerActivity() {
        PlayerActivity.launch(this)
    }

    /**
     * FragmentContainer
     */

    override fun setActivityTitle(title: String) { }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        setSupportActionBar(toolbar_navigation)

        layout_now_playing.setOnClickListener { presenter.onNowPlayingClicked() }
        fab_play_pause.setOnClickListener { presenter.onPlayFabClicked() }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_navigation
    }

    override fun getContentLayout(): FrameLayout {
        return frame_fragment
    }

    override fun setTitle(title: String) {
        this.title = title
    }

    private fun getFragment(): NavigationFragment? {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_fragment) as NavigationFragment
        return fragment
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.navigation"

        val ARGUMENT_FRAGMENT_TAG = "${ACTIVITY_TAG}.fragment_tag"
        val ARGUMENT_FRAGMENT_ARG = "${ACTIVITY_TAG}.fragment_argument"
        val ARGUMENT_TITLE = "${ACTIVITY_TAG}.title"

        fun launch(context: Context, fragmentTag: String, fragmentArg: Long, title: String) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)
            launcher.putExtra(ARGUMENT_FRAGMENT_ARG, fragmentArg)
            launcher.putExtra(ARGUMENT_TITLE, title)

            context.startActivity(launcher)
        }
    }
}