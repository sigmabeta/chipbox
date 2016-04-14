package net.sigmabeta.chipbox.ui.game

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePresenter @Inject constructor(val player: Player) : ActivityPresenter() {
    var view: GameView? = null

    var gameId: Long? = null

    var game: Game? = null
    var songs: MutableList<Track>? = null

    fun onItemClick(position: Long) {
        songs?.let {
            player.play(it, position.toInt())
        }
    }

    fun onClick(clickedId: Int) {
        when (clickedId) {
            R.id.button_fab -> {
                songs?.let { them ->
                    player.play(them, 0)
                }
            }
        }
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
        val gameId = arguments?.getLong(GameActivity.ARGUMENT_GAME_ID) ?: -1
        this.gameId = gameId

        val gameSubscription = Game.get(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe (
                        { game ->
                            this.game = game
                            view?.setGame(game)

                            val tracks = game.getTracks()
                            this.songs = tracks
                            view?.setSongs(tracks)
                        },
                        {
                            view?.setGame(null)
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )

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
            view?.setSongs(it)
        }

        player.playingTrack?.let {
            displayTrack(it)
        }

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> {
                            displayTrack(it.track)
                        }
                        is PositionEvent -> { /* no-op */ }
                        is StateEvent -> { /* no-op */
                        }
                        else -> logWarning("[GamePresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is GameView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    override fun onReenter() = Unit

    private fun displayTrack(track: Track) {
        view?.setPlayingTrack(track)
    }
}
