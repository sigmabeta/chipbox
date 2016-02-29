package net.sigmabeta.chipbox.presenter

import android.database.Cursor
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.view.interfaces.SongListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import javax.inject.Inject

@FragmentScoped
class SongListPresenter @Inject constructor(val view: SongListView,
                                            val database: SongDatabaseHelper,
                                            val player: Player) {
    var artist = Track.PLATFORM_ALL.toLong()

    var gameMap: HashMap<Long, Game>? = null

    fun onCreate(artist: Long) {
        this.artist = artist
    }

    fun onCreateView() {
        val readOperation = if (artist == Track.PLATFORM_ALL.toLong()) {
            database.getSongList()
        } else {
            database.getSongListForArtist(artist)
        }

        readOperation.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            logInfo("[SongListPresenter] Loaded ${it.count} tracks.")
                            loadGames(it)
                        }
                )
    }

    fun loadGames(tracks: Cursor) {
        database.getGamesForTrackCursor(tracks)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            logInfo("[SongListPresenter] Loaded ${it.size} games.")
                            gameMap = it
                            view.setCursor(tracks)
                        }
                )
    }

    fun onItemClick(track: Track, position: Int) {
        val cursor = view.getCursor()

        if (cursor != null) {
            val queue = SongDatabaseHelper.getPlaybackQueueFromCursor(cursor)
            player.play(queue, position)
        }
    }

    fun getImagePath(gameId: Long): String? {
        val game = gameMap?.get(gameId)

        return game?.artLocal
    }
}