package net.sigmabeta.chipbox.presenter

import android.database.Cursor
import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.view.activity.GameActivity
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.GameView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class GamePresenter @Inject constructor(val database: SongDatabaseHelper,
                                        val player: Player) : ActivityPresenter() {
    var view: GameView? = null

    var gameId: Long? = null

    var game: Game? = null
    var songs: Cursor? = null

    fun onItemClick(track: Track, position: Int) {
        songs?.let {
            val queue = SongDatabaseHelper.getPlaybackQueueFromCursor(it)
            player.play(queue, position)
        }
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
        val gameId = arguments?.getLong(GameActivity.ARGUMENT_GAME_ID) ?: -1
        this.gameId = gameId

        val songsSubscription = database.getSongListForGame(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    songs = it
                    view?.setCursor(it)
                }


        val gameSubscription = database.getGame(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    game = it
                    view?.setGame(it)
                }

        subscriptions.add(songsSubscription)
        subscriptions.add(gameSubscription)
    }

    override fun teardown() {
        gameId = null
        game = null
        songs = null
    }

    override fun updateViewState() {
        game?.let {
            view?.setGame(it)
        }

        songs?.let {
            view?.setCursor(it)
        }

        player.playingTrack?.let {
            setPlayingTrack(it)
        }

        val subscription = player.updater.asObservable()
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

        subscriptions.add(subscription)
    }

    override fun setView(view: BaseView) {
        if (view is GameView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun setPlayingTrack(track: Track) {
        view?.setPlayingTrack(track)
    }

    private fun setPlaybackState(state: Int) {
        view?.setPlaybackState(state)
    }
}
