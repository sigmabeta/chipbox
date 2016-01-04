package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_game.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.model.database.COLUMN_TRACK_GAME_ID
import net.sigmabeta.chipbox.model.database.COLUMN_TRACK_GAME_TITLE
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.presenter.GamePresenter
import net.sigmabeta.chipbox.view.adapter.SongListAdapter
import net.sigmabeta.chipbox.view.interfaces.GameView
import net.sigmabeta.chipbox.view.interfaces.SongListView
import javax.inject.Inject

class GameActivity : BaseActivity(), GameView, SongListView {
    lateinit var presenter: GamePresenter
        @Inject set

    var adapter: SongListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        val launcher = intent

        val gameId = launcher.getLongExtra(ARGUMENT_GAME_ID, -1)

        presenter.onCreate(gameId)

        val layoutManager = LinearLayoutManager(this)

        val adapter = SongListAdapter(this, this, false)
        this.adapter = adapter

        list_tracks.adapter = adapter
        list_tracks.layoutManager = layoutManager
    }

    /**
     * GameView
     */

    override fun setCursor(cursor: Cursor) {
        cursor.moveToFirst()
        adapter?.changeCursor(cursor)

        val gameId = cursor.getLong(COLUMN_TRACK_GAME_ID)
        val imagePath = adapter?.imagesPath + gameId.toString() + "/local.png"

        Picasso.with(this)
                .load(imagePath)
                .centerCrop()
                .fit()
                .into(image_hero_boxart)

        val gameTitle = cursor.getString(COLUMN_TRACK_GAME_TITLE)

        collapsing_toolbar.title = gameTitle
    }

    override fun getCursor(): Cursor? {
        return adapter?.cursor
    }

    /**
     * SongListView
     */

    override fun onItemClick(track: Track, position: Int) {
        presenter.onItemClick(track, position)
    }

    override fun launchPlayerActivity() {
        // No-op
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
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