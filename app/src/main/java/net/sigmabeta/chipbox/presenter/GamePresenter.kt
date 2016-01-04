package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.GameView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class GamePresenter @Inject constructor(val view: GameView,
                                        val database: SongDatabaseHelper,
                                        val player: Player) {
    var gameId: Long? = null

    var game: Game? = null

    fun onCreate(gameId: Long) {
        this.gameId = gameId

        database.getSongListForGame(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            view.setCursor(it)
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
}
