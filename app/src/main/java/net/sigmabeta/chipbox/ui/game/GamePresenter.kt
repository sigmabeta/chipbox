package net.sigmabeta.chipbox.ui.game

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePresenter @Inject constructor(val player: Player,
                                        val updater: UiUpdater) : ActivityPresenter<GameView>() {
    var gameId: String? = null

    var game: Game? = null
    var tracks: List<Track>? = null

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
        setupHelper(arguments)
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (tracks == null) {
            setupHelper(arguments)
        }
    }

    override fun onTempDestroy() = Unit

    override fun teardown() {
        gameId = null
        game = null
        tracks = null
    }

    override fun updateViewState() {
        game?.let {
            view?.setGame(it)
        }

        tracks?.let {
            view?.setTracks(it)
        }

        displayTrack(player.playlist.playingTrackId)

        val subscription = updater.asObservable()
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

    private fun setupHelper(arguments: Bundle?) {
        loading = true

        val gameId = arguments?.getString(GameActivity.ARGUMENT_GAME_ID)
        this.gameId = gameId

        gameId?.let {
            val gameSubscription = repository.getGame(it)
                    .subscribe(
                            { game ->
                                loading = false

                                if (game != null) {
                                    this.game = game
                                    view?.setGame(game)

                                    val tracks = game.tracks?.toMutableList()

                                    tracks?.let {
                                        this.tracks = tracks
                                        view?.setTracks(tracks)
                                    }
                                } else {
                                    view?.setGame(null)
                                    view?.showErrorSnackbar("Error: Game not found.", null, null)
                                }
                            },
                            {
                                loading = false

                                view?.setGame(null)
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(gameSubscription)
        }
    }
}
