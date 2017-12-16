package net.sigmabeta.chipbox.ui.navigation

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationPresenter @Inject constructor(val player: Player,
                                              val playlist: Playlist,
                                              val updater: UiUpdater) : ActivityPresenter<NavigationView>() {
    // A property is kept in order to be able to track changes in state.
    var playerState = player.state

    var game: Game? = null

    fun onNowPlayingClicked() {
        view?.launchPlayerActivity()
    }

    fun onPlayFabClicked() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()

            PlaybackState.STATE_PAUSED -> player.start(null)

            PlaybackState.STATE_STOPPED -> player.start(null)
        }
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun setup(arguments: Bundle?) {
        state = UiState.READY

        val fragmentTag = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_TAG)
        val fragmentArg = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_ARG_STRING)

        if (fragmentTag != null) {
            view?.showFragment(fragmentTag, fragmentArg)
        }
    }

    override fun teardown() {
        playerState = -1
        game = null
    }

    override fun showReadyState() {
        updateHelper()

        val subscription = updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.trackId, true)
                        is PositionEvent -> { /* no-op */ }
                        is GameEvent -> displayGame(it.gameId, false)
                        is StateEvent -> displayState(playerState, it.state)
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) = Unit

    override fun onReenter() {
        updateHelper()
    }


    private fun displayState(oldState: Int, newState: Int) {
        when (newState) {
            PlaybackState.STATE_PLAYING -> {
                view?.showPauseButton()
                view?.showNowPlaying(oldState == PlaybackState.STATE_STOPPED)
            }

            PlaybackState.STATE_PAUSED -> {
                view?.showPlayButton()
                view?.showNowPlaying(oldState == PlaybackState.STATE_STOPPED)
            }

            PlaybackState.STATE_STOPPED -> {
                view?.hideNowPlaying(oldState != PlaybackState.STATE_STOPPED)
            }
        }

        this.playerState = newState
    }

    private fun displayTrack(trackId: String?, animate: Boolean) {
        if (trackId != null) {
            val track = repository.getTrackSync(trackId)

            if (track != null) {
                view?.setTrackTitle(track.title.orEmpty(), animate)
                view?.setArtist(track.artistText.orEmpty(), animate)
            } else {
                Timber.e("Cannot load track with id %s", trackId)
            }
        }
    }

    private fun updateHelper() {
        playlist.playingTrackId?.let {
            displayTrack(it, false)
        }

        playlist.playingGameId?.let {
            displayGame(it, true)
        }

        displayState(playerState, player.state)
    }

    private fun displayGame(gameId: String?, force: Boolean) {
        if (gameId != null) {
            val game = repository.getGameSync(gameId)

            if (force || this.game !== game) {
                view?.setGameBoxArt(game?.artLocal, !force)
            }

            this.game = game
        }
    }
}
