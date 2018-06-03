package net.sigmabeta.chipbox.ui

import android.media.session.PlaybackState
import android.os.Bundle
import android.support.annotation.CallSuper
import io.reactivex.android.schedulers.AndroidSchedulers
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import timber.log.Timber

abstract class ChromePresenter<V : ChromeView>(val player: Player,
                                               val scanner: LibraryScanner,
                                               val playlist: Playlist,
                                               val updater: UiUpdater) : ActivityPresenter<V>() {
    var nowPlayingGame: Game? = null

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

    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.drawer_refresh -> view?.startScanner()
            R.id.drawer_settings -> view?.launchSettingsActivity()
            R.id.drawer_debug -> view?.launchDebugActivity()
            R.id.drawer_help -> view?.launchOnboarding()
        }

        return true
    }

    /**
     * ActivityPresenter
     */

    override fun onReenter() {
        updateHelper()
    }

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun onClick(id: Int) = Unit

    @CallSuper
    override fun showReadyState() {
        updateHelper()

        val subscription = updater.asFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.trackId, true)
                        is PositionEvent -> {
                            /* no-op */
                        }
                        is GameEvent -> displayGame(it.gameId, false)
                        is StateEvent -> displayState(it.state)
                        is FileScanEvent -> view?.showScanning(it.type, it.name)
                        is FileScanFailedEvent -> {
                            handleError(it.exception)
                            view?.hideScanning()
                        }
                        is FileScanCompleteEvent -> {
                            view?.showFileScanSuccess(it.newTracks, it.updatedTracks)
                            view?.hideScanning()
                        }
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun setup(arguments: Bundle?) {
        state = UiState.READY
    }

    override fun teardown() {
        nowPlayingGame = null
    }

    /**
     * Implementation Details
     */

    private fun updateHelper() {
        playlist.playingTrackId?.let {
            displayTrack(it, false)
        }

        playlist.playingGameId?.let {
            displayGame(it, true)
        }

        displayState(player.state)
    }

    private fun displayState(newState: Int) {
        when (newState) {
            PlaybackState.STATE_PLAYING -> {
                view?.showPauseButton()
                view?.showNowPlaying()
            }

            PlaybackState.STATE_PAUSED -> {
                view?.showPlayButton()
                view?.showNowPlaying()
            }

            PlaybackState.STATE_STOPPED -> {
                view?.hideNowPlaying()
            }
        }

        if (scanner.state == LibraryScanner.STATE_SCANNING) {
            view?.showScanning(null, null)
        } else {
            view?.hideScanning()
        }
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

    private fun displayGame(gameId: String?, force: Boolean) {
        if (gameId != null) {
            val game = repository.getGameSync(gameId)

            if (force || this.nowPlayingGame !== game) {
                view?.setGameBoxArt(game?.artLocal, !force)
            }

            this.nowPlayingGame = game
        }
    }
}