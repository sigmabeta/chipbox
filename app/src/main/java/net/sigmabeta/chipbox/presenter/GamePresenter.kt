package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.view.interfaces.GameView
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class GamePresenter @Inject constructor(val view: GameView,
                                        val database: SongDatabaseHelper,
                                        val player: Player) {
    var gameId: Long? = null

    var game: Game? = null

    var subscription: Subscription? = null

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

        val track = player.playingTrack

        if (track != null) {
            setPlayingTrack(track)
        }
    }

    fun onResume() {
        val track = player.playingTrack
        if (track != null) {
            setPlayingTrack(track)
        }

        subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> {
                            setPlayingTrack(it.track)
                        }
                        is PositionEvent -> { /* no-op */ }
                        is StateEvent -> {
                            setPlaybackState(it.state)
                        }
                        else -> logWarning("[GamePresenter] Unhandled ${it}")
                    }
                }
    }

    fun onPause() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun setPlayingTrack(track: Track) {
        view.setPlayingTrack(track)
    }

    private fun setPlaybackState(state: Int) {
        view.setPlaybackState(state)
    }

    fun onItemClick(track: Track, position: Int) {
        val cursor = view.getCursor()

        if (cursor != null) {
            val queue = SongDatabaseHelper.getPlaybackQueueFromCursor(cursor)
            player.play(queue, position)
        }
    }
}
