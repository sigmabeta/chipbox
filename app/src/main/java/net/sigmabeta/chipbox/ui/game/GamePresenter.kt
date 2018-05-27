package net.sigmabeta.chipbox.ui.game

import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePresenter @Inject constructor(val player: Player,
                                        val updater: UiUpdater) : ActivityPresenter<GameView>() {
    var gameId: String? = null

    var game: Game? = null
    var tracks: List<Track>? = null

    private var width = -1
    private var height = -1

    fun onItemClick(position: Int) {
        getTrackIdList()?.let {
            player.play(it, position)
        }
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_fab -> {
                getTrackIdList()?.let { them ->
                    player.play(them, 0)
                }
            }
        }
    }

    override fun setup(arguments: Bundle?) {
        state = UiState.LOADING

        gameId = arguments?.getString(GameActivity.ARGUMENT_GAME_ID)
        width = arguments?.getInt(GameActivity.ARGUMENT_GAME_IMAGE_WIDTH) ?: -1
        height = arguments?.getInt(GameActivity.ARGUMENT_GAME_IMAGE_HEIGHT) ?: -1

        this.gameId = gameId

        gameId?.let {
            val gameSubscription = repository.getGame(it)
                    .subscribe(
                            { game ->
                                if (game != null) {
                                    this.game = game
                                    this.tracks = game.tracks?.toMutableList()

                                    state = UiState.READY
                                } else {
                                    handleError(RuntimeException("Game not found."))
                                }
                            },
                            {
                                handleError(it)
                            }
                    )

            subscriptions.add(gameSubscription)
        }
    }

    override fun onTempDestroy() = Unit

    override fun teardown() {
        gameId = null
        game = null
        tracks = null
        width = -1
        height = -1
    }

    override fun showReadyState() {
        game?.let {
            view?.setGame(it, width, height)
        }

        tracks?.let {
            view?.setTracks(it)
        }

        displayTrack(player.playlist.playingTrackId)

        val subscription = updater.asFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> {
                            displayTrack(it.trackId)
                        }
                        is PositionEvent -> { /* no-op */ }
                        is StateEvent -> { /* no-op */
                        }
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onReenter() = Unit

    private fun getTrackIdList() = tracks?.map(Track::id)?.toMutableList()

    private fun displayTrack(trackId: String?) {
        if (trackId != null) {
            val track = repository.getTrackSync(trackId)

            if (track != null) {
                view?.setPlayingTrack(track)
            } else {
                Timber.e("Cannot load track with id %s", trackId)
            }
        }
    }

}
