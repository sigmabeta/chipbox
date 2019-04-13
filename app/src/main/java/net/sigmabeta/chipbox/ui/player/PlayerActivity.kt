package net.sigmabeta.chipbox.ui.player

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.app.SharedElementCallback
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Pair
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_player.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.playlist.PlaylistFragment
import net.sigmabeta.chipbox.util.animation.*
import net.sigmabeta.chipbox.util.loadImageHighQuality
import javax.inject.Inject

class PlayerActivity : BaseActivity<PlayerActivityPresenter, PlayerActivityView>(),
        PlayerActivityView,
        FragmentContainer {
    lateinit var presenter: PlayerActivityPresenter
        @Inject set

    /**
     * PlayerActivityView
     */

    override fun onPlaylistFabClicked() {
        presenter.onClick(R.id.button_fab)
    }

    override fun callFinish() {
        supportFinishAfterTransition()
    }

    override fun showPlaylistScreen() {
        NavigationActivity.launch(this, PlaylistFragment.FRAGMENT_TAG, "")
    }

    override fun hideStatusBar() {
        frame_content.fitsSystemWindows = false
        animateStatusBar(ContextCompat.getColor(this, R.color.primary_dark),
                ContextCompat.getColor(this, R.color.grey_translucent))
    }

    /**
     * PlayerControlsView
     */

    override fun showPauseButton() {
        button_play.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play.setImageResource(R.drawable.ic_play_arrow_black_24dp)
    }

    override fun setShuffleEnabled() {
        setViewTint(button_shuffle, R.color.accent)
    }

    override fun setShuffleDisabled() {
        setViewTint(button_shuffle, R.color.circle_grey)
    }

    override fun setRepeatDisabled() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.circle_grey)
    }

    override fun setRepeatAll() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.accent)
    }

    override fun setRepeatOne() {
        button_repeat.setImageResource(R.drawable.ic_repeat_one_black_24dp)
        setViewTint(button_repeat, R.color.accent)
    }

    override fun setRepeatInfinite() {
        button_repeat.setImageResource(R.drawable.ic_repeat_black_24dp)
        setViewTint(button_repeat, R.color.primary)
    }

    /**
     * PlayerFragmentView
     */

    override fun setTrackTitle(title: String, animate: Boolean) {
        if (animate) {
            text_playing_title.changeText(title)
        } else {
            text_playing_title.text = title
        }
    }

    override fun setGameTitle(title: String, animate: Boolean) {
        if (animate) {
            text_game_title.changeText(title)
        } else {
            text_game_title.text = title
        }
    }

    override fun setArtist(artist: String, animate: Boolean) {
        if (animate) {
            text_playing_subtitle.changeText(artist)
        } else {
            text_playing_subtitle.text = artist
        }
    }

    override fun setTimeElapsed(time: String) {
        text_track_elapsed.text = time
    }

    override fun setTrackLength(trackLength: String, animate: Boolean) {
        if (animate) {
            text_track_length.changeText(trackLength)
        } else {
            text_track_length.text = trackLength
        }
    }

    override fun setGameBoxArt(path: String?, fade: Boolean) {
        image_main.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                image_main.viewTreeObserver.removeOnPreDrawListener(this)
                if (path != null) {
                    image_main.loadImageHighQuality(path, fade, 1.0f, getPicassoCallback())
                } else {
                    image_main.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, fade, null, getPicassoCallback())
                }
                return true
            }
        })
    }

    override fun setSeekProgress(percentPlayed: Int) {
        seek_playback_progress.progress = percentPlayed
    }

    /**
     * FragmentContainer
     */

    override fun setTitle(title: String) {
        setTitle(title)
    }

    /**
     * BaseActivity
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject() {
        getTypedApplication().appComponent.inject(this)
    }

    override fun getPresenterImpl() = presenter

    override fun configureViews() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        button_play.setOnClickListener(this)
        button_skip_back.setOnClickListener(this)
        button_skip_forward.setOnClickListener(this)
        button_shuffle.setOnClickListener(this)
        button_repeat.setOnClickListener(this)
        button_fab.setOnClickListener(this)

        seek_playback_progress.setOnSeekBarChangeListener(presenter)

        ReflowText.reflowDataFromIntent(intent, text_playing_title)
        ReflowText.reflowDataFromIntent(intent, text_playing_subtitle)

        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                sharedElements["ignored0"] = button_fab
                sharedElements["ignored1"] = text_game_title
                sharedElements["ignored2"] = seek_playback_progress
                sharedElements["ignored3"] = text_track_elapsed
                sharedElements["ignored4"] = text_track_length
                sharedElements["ignored5"] = button_repeat
                sharedElements["ignored6"] = button_skip_back
                sharedElements["ignored7"] = button_skip_forward
                sharedElements["ignored8"] = button_shuffle
            }

            override fun onSharedElementStart(sharedElementNames: List<String>, sharedElements: List<View>, sharedElementSnapshots: List<View>) {
                ReflowText.reflowDataFromIntent(intent, text_playing_title)
                ReflowText.reflowDataFromIntent(intent, text_playing_subtitle)
            }

            override fun onSharedElementEnd(sharedElementNames: List<String>, sharedElements: List<View>, sharedElementSnapshots: List<View>) {
                ReflowText.reflowDataFromView(ReflowableTextView(text_playing_title))
                ReflowText.reflowDataFromView(ReflowableTextView(text_playing_subtitle))
            }
        })
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_player
    }

    override fun getContentLayout() = frame_content

    override fun getSharedImage() = image_main

    override fun shouldDelayTransitionForFragment() = true

    /**
     * Private Methods
     */

    private fun animateStatusBar(fromColor: Int, toColor: Int) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)

        colorAnimation.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            window.statusBarColor = color
        }

        colorAnimation.duration = 300L
        colorAnimation.interpolator = ACC_DECELERATE

        colorAnimation.start()
    }

    private fun setViewTint(view: ImageView, colorId: Int) {
        val color = ContextCompat.getColor(this, colorId)
        view.drawable.setTint(color)
    }

    companion object {
        fun launch(activity: Activity,
                   navBar: Pair<View, String>?,
                   statusBar: Pair<View, String>?,
                   imageView: Pair<View, String>,
                   titleText: Pair<View, String>,
                   subtitleText: Pair<View, String>,
                   playButton: Pair<View, String>,
                   background: Pair<View, String>) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                PlayerActivity.launch(activity)
                return
            }

            val launcher = Intent(activity, PlayerActivity::class.java)

            launcher.putExtra(GameActivity.ARGUMENT_GAME_IMAGE_WIDTH, imageView.first?.width)
            launcher.putExtra(GameActivity.ARGUMENT_GAME_IMAGE_HEIGHT, imageView.first?.height)

            ReflowText.addExtras(launcher, ReflowableTextView(titleText.first as CustomTextView))
            ReflowText.addExtras(launcher, ReflowableTextView(subtitleText.first as CustomTextView))

            val sharedViewPairs = removeNullViewPairs(navBar,
                    statusBar,
                    imageView,
                    titleText,
                    subtitleText,
                    playButton,
                    background)
            val options = ActivityOptions.makeSceneTransitionAnimation(activity, *sharedViewPairs)

            activity.startActivity(launcher, options.toBundle())
        }

        fun launch(context: Context) {
            val launcher = Intent(context, PlayerActivity::class.java)
            context.startActivity(launcher)
        }

        fun getLauncher(context: Context): Intent {
            return Intent(context, PlayerActivity::class.java)
        }
    }
}
