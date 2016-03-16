package net.sigmabeta.chipbox.ui.game

import android.content.Context
import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_game.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ItemListView
import net.sigmabeta.chipbox.ui.song.SongListAdapter
import net.sigmabeta.chipbox.ui.song.SongListView
import net.sigmabeta.chipbox.util.loadImageHighQuality
import java.util.*
import javax.inject.Inject

class GameActivity : BaseActivity(), GameView, ItemListView, SongListView {
    lateinit var presenter: GamePresenter
        @Inject set

    var adapter = SongListAdapter(this, false)

    /**
     * GameView
     */

    override fun setGame(game: Game) {
        val imagePath = game.artLocal

        if (imagePath != null) {
            image_hero_boxart.loadImageHighQuality(imagePath, false, false)
        } else {
            image_hero_boxart.loadImageHighQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, false, false)
        }

        collapsing_toolbar.title = game.title
    }

    override fun setPlayingTrack(track: Track) {
        adapter.playingTrackId = track.id
    }

    override fun setPlaybackState(state: Int) {
        // no-op for now
    }

    /**
     * SongListView
     */

    override fun refreshList() {
        adapter.notifyDataSetChanged()
    }

    override fun setSongs(songs: ArrayList<Track>) {
        adapter.dataset = songs
    }

    override fun setGames(games: HashMap<Long, Game>) {
        adapter.games = games
    }

    override fun setActivityTitle(name: String) {
        this.title = title
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Long) {
        presenter.onItemClick(position)
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(this)

        list_tracks.adapter = adapter
        list_tracks.layoutManager = layoutManager
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_game
    }

    override fun getContentLayout(): FrameLayout {
        return findViewById(android.R.id.content) as FrameLayout
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.game"

        val ARGUMENT_GAME_ID = "${ACTIVITY_TAG}.game_id"

        fun launch(context: Context, gameId: Long) {
            val launcher = Intent(context, GameActivity::class.java)

            launcher.putExtra(ARGUMENT_GAME_ID, gameId)

            context.startActivity(launcher)
        }
    }
}