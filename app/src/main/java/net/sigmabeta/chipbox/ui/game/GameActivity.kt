package net.sigmabeta.chipbox.ui.game

import android.app.Activity
import android.app.ActivityOptions
import android.app.SharedElementCallback
import android.content.Context
import android.content.Intent
import android.os.Build
import android.transition.Transition
import android.util.Pair
import android.view.View
import android.view.ViewTreeObserver
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.OrderedCollectionChangeSet
import kotlinx.android.synthetic.main.activity_game.*
import kotlinx.android.synthetic.main.list_header_game.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.ChromeActivity
import net.sigmabeta.chipbox.ui.ListView
import net.sigmabeta.chipbox.util.animation.*
import net.sigmabeta.chipbox.util.calculateAspectRatio
import net.sigmabeta.chipbox.util.loadImageHighQuality
import net.sigmabeta.chipbox.util.loadImageSetSize
import javax.inject.Inject

class GameActivity : ChromeActivity<GamePresenter, GameView>(), GameView, ListView<Track, GameTrackViewHolder> {
    lateinit var presenter: GamePresenter
        @Inject set

    var adapter = GameTrackListAdapter(this)

    private var imagePath: String? = null

    private var aspectRatio: Float? = null

    /**
     * ListView
     */

    override fun onItemClick(position: Int) {
        if (position > 0L) {
            presenter.onItemClick(position - 1)
        }
    }

    override fun setList(list: List<Track>) = Unit

    override fun animateChanges(changeset: OrderedCollectionChangeSet) = Unit

    override fun refreshList() = Unit

    override fun startRescan() = Unit

    override fun showScanningWaitMessage() = Unit

    /**
     * GameView
     */

    override fun setGame(game: Game, width: Int, height: Int) {
        adapter.game = game
        imagePath = game.artLocal

        aspectRatio = calculateAspectRatio(width, height)
        val imagePath = game.artLocal

        image_main.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                image_main.viewTreeObserver.removeOnPreDrawListener(this)
                if (width > 0 || height > 0) {
                    if (imagePath != null) {
                        image_main.loadImageSetSize(imagePath, width, height, false, getPicassoCallback())
                    } else {
                        image_main.loadImageSetSize(Game.PICASSO_ASSET_ALBUM_ART_BLANK, width, height, false, getPicassoCallback())
                    }
                } else {
                    if (imagePath != null) {
                        image_main.loadImageHighQuality(imagePath, true, null)
                    } else {
                        image_main.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, true, null)
                    }
                }
                return true
            }
        })

        window.sharedElementEnterTransition.addListener(
                object : Transition.TransitionListener {
                    override fun onTransitionResume(p0: Transition?) = Unit
                    override fun onTransitionPause(p0: Transition?) = Unit
                    override fun onTransitionCancel(p0: Transition?) = Unit
                    override fun onTransitionStart(p0: Transition?) = Unit

                    override fun onTransitionEnd(p0: Transition?) {
                        imagePath?.let {
                            image_main.loadImageHighQuality(it, false, aspectRatio, getPicassoCallback())
                        } ?: let {
                            image_main.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, false, aspectRatio, getPicassoCallback())
                        }
                    }
                })
    }

    override fun setPlayingTrack(track: Track) {
        adapter.playingTrackId = track.id
    }

    override fun setTracks(tracks: List<Track>) {
        adapter.dataset = tracks
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

    override fun getScrollingContentView() = list_tracks

    override fun isScrolledToBottom() = getScrollingContentView().isScrolledToBottom()

    /**
     * BaseActivity
     */

    override fun configureViews() {
        super.configureViews()

        val layoutManager = LinearLayoutManager(this)

        list_tracks.adapter = adapter
        list_tracks.layoutManager = layoutManager
        list_tracks.clipChildren = false

        button_fab.setOnClickListener {
            presenter.onClick(it.id)
        }

        val listener = object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition?) = setFabAutohide(true)

            override fun onTransitionStart(transition: Transition?) = Unit
            override fun onTransitionResume(transition: Transition?) = Unit
            override fun onTransitionPause(transition: Transition?) = Unit
            override fun onTransitionCancel(transition: Transition?) = Unit
        }

        window.sharedElementEnterTransition.addListener(listener)

        setFabAutohide(false)

        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                sharedElements["header_text_title"] = text_title
                sharedElements["subheader_text_subtitle"] = text_subtitle
                sharedElements["ignored"] = button_fab
            }

            override fun onSharedElementStart(sharedElementNames: List<String>, sharedElements: List<View>, sharedElementSnapshots: List<View>) {
                ReflowText.reflowDataFromIntent(intent, text_title)
                ReflowText.reflowDataFromIntent(intent, text_subtitle)
            }

            override fun onSharedElementEnd(sharedElementNames: List<String>, sharedElements: List<View>, sharedElementSnapshots: List<View>) {
                ReflowText.reflowDataFromView(ReflowableTextView(text_title))
                ReflowText.reflowDataFromView(ReflowableTextView(text_subtitle))
            }
        })
    }

    override fun getContentLayoutId() = R.layout.activity_game

    override fun getContentLayout() = main_content

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getSharedImage() = image_main

    override fun shouldDelayTransitionForFragment() = false

    /**
     * Activity
     */

    override fun onBackPressed() {
        setFabAutohide(false)
        super.onBackPressed()
    }

    /**
     * Implementation Details
     */

    private fun setFabAutohide(enabled: Boolean) {
        val layoutParams = button_fab.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = FloatingActionButton.Behavior()

        behavior.isAutoHideEnabled = enabled
        layoutParams.behavior = behavior
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.game"

        val ARGUMENT_GAME_ID = "${ACTIVITY_TAG}.game_id"
        val ARGUMENT_GAME_IMAGE_WIDTH = "${ACTIVITY_TAG}.image.width"
        val ARGUMENT_GAME_IMAGE_HEIGHT = "${ACTIVITY_TAG}.image.height"

        fun launch(context: Context, gameId: String) {
            val launcher = Intent(context, GameActivity::class.java)

            launcher.putExtra(ARGUMENT_GAME_ID, gameId)

            context.startActivity(launcher)
        }

        fun launch(activity: Activity,
                   gameId: String,
                   navBar: Pair<View, String>?,
                   statusBar: Pair<View, String>?,
                   bottomBar: Pair<View, String>?,
                   imageView: Pair<View, String>,
                   titleText: Pair<View, String>,
                   subtitleText: Pair<View, String>,
                   background: Pair<View, String>) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                launch(activity, gameId)
                return
            }

            val launcher = Intent(activity, GameActivity::class.java)

            launcher.putExtra(ARGUMENT_GAME_ID, gameId)
            launcher.putExtra(ARGUMENT_GAME_IMAGE_WIDTH, imageView.first?.width)
            launcher.putExtra(ARGUMENT_GAME_IMAGE_HEIGHT, imageView.first?.height)

            ReflowText.addExtras(launcher, ReflowableTextView(titleText.first as CustomTextView))
            ReflowText.addExtras(launcher, ReflowableTextView(subtitleText.first as CustomTextView))

            val sharedViewPairs = removeNullViewPairs(navBar,
                    statusBar,
                    bottomBar,
                    imageView,
                    titleText,
                    subtitleText,
                    background)
            val options = ActivityOptions.makeSceneTransitionAnimation(activity, *sharedViewPairs)

            activity.startActivity(launcher, options.toBundle())
        }
    }
}