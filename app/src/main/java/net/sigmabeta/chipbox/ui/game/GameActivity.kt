package net.sigmabeta.chipbox.ui.game

import android.app.Activity
import android.app.ActivityOptions
import android.app.SharedElementCallback
import android.content.Context
import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.transition.Transition
import android.util.Pair
import android.view.View
import io.realm.OrderedCollectionChangeSet
import kotlinx.android.synthetic.main.activity_game.*
import kotlinx.android.synthetic.main.list_header_game.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListView
import net.sigmabeta.chipbox.util.animation.CustomTextView
import net.sigmabeta.chipbox.util.animation.ReflowText
import net.sigmabeta.chipbox.util.animation.ReflowableTextView
import net.sigmabeta.chipbox.util.animation.removeNullViewPairs
import net.sigmabeta.chipbox.util.calculateAspectRatio
import net.sigmabeta.chipbox.util.loadImageHighQuality
import net.sigmabeta.chipbox.util.loadImageSetSize
import javax.inject.Inject

class GameActivity : BaseActivity<GamePresenter, GameView>(), GameView, ListView<Track, GameTrackViewHolder> {
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

    /**
     * GameView
     */


    override fun setGame(game: Game, width: Int, height: Int) {
        adapter.game = game
        imagePath = game.artLocal

        aspectRatio = calculateAspectRatio(width, height)
        val imagePath = game.artLocal

        if (imagePath != null) {
            image_main.loadImageSetSize(imagePath, width, height, false, getPicassoCallback())
        } else {
            image_main.loadImageSetSize(Game.PICASSO_ASSET_ALBUM_ART_BLANK, width, height, false, getPicassoCallback())
        }

        window.sharedElementEnterTransition.addListener(object : Transition.TransitionListener {
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

    override fun setList(list: List<Track>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun animateChanges(changeset: OrderedCollectionChangeSet) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isScrolledToBottom(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun startRescan() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setPlayingTrack(track: Track) {
        adapter.playingTrackId = track.id
    }

    override fun setTracks(tracks: List<Track>) {
        adapter.dataset = tracks
    }

    /**
     * BaseActivity
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject() {
        getTypedApplication().appComponent.inject(this)
    }

    override fun getPresenterImpl(): GamePresenter {
        return presenter
    }

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(this)

        list_tracks.adapter = adapter
        list_tracks.layoutManager = layoutManager
        list_tracks.clipChildren = false

        button_fab.setOnClickListener {
            presenter.onClick(it.id)
        }

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



    override fun getLayoutId(): Int {
        return R.layout.activity_game
    }

    override fun getContentLayout() = main_content

    override fun getSharedImage(): View? = image_main

    override fun shouldDelayTransitionForFragment() = false

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
                   imageView: Pair<View, String>,
                   titleText: Pair<View, String>,
                   subtitleText: Pair<View, String>,
                   background: Pair<View, String>) {
            val launcher = Intent(activity, GameActivity::class.java)

            launcher.putExtra(ARGUMENT_GAME_ID, gameId)
            launcher.putExtra(ARGUMENT_GAME_IMAGE_WIDTH, imageView.first?.width)
            launcher.putExtra(ARGUMENT_GAME_IMAGE_HEIGHT, imageView.first?.height)

            ReflowText.addExtras(launcher, ReflowableTextView(titleText.first  as CustomTextView))
            ReflowText.addExtras(launcher, ReflowableTextView(subtitleText.first as CustomTextView))

            val sharedViewPairs = removeNullViewPairs(navBar,
                    statusBar,
                    imageView,
                    titleText,
                    subtitleText,
                    background)
            val options = ActivityOptions.makeSceneTransitionAnimation(activity, *sharedViewPairs)

            activity.startActivity(launcher, options.toBundle())
        }
    }
}