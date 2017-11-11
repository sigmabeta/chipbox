package net.sigmabeta.chipbox.ui.player

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class PlayerFragmentPresenter @Inject constructor(val player: Player,
                                                  val playlist: Playlist,
                                                  val updater: UiUpdater) : FragmentPresenter<PlayerFragmentView>() {
    var game: Game? = null

    var track: Track? = null

    var seekbarTouched = false

    fun onFabClick() {
        view?.showPlaylist()
    }

    fun onSeekbarChanged(progress: Int) {
        val length = track?.trackLength ?: 0
        val seekPosition = (length * progress / 100)

        displayTimeString(seekPosition)
    }

    fun onSeekbarTouch() {
        seekbarTouched = true
    }

    fun onSeekbarRelease(progress: Int) {
        val length = track?.trackLength ?: 0
        val seekPosition = (length * progress / 100)
        player.seek(seekPosition)

        // TODO This is a hack
        Observable.just(1)
                .delay(66L, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe {
                    seekbarTouched = false
                }
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun setup(arguments: Bundle?) {
        needsSetup = false
    }

    override fun teardown() {
        track = null
        seekbarTouched = false
    }

    override fun updateViewState() {
        updateHelper()

        val subscription = updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.trackId, true)
                        is PositionEvent -> displayPosition(it.millisPlayed)
                        is StateEvent -> displayState(it.state)
                        is GameEvent -> displayGame(it.gameId, false, true)
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) = Unit

    private fun updateHelper() {
        playlist.playingTrackId?.let {
            displayTrack(it, false)
        } ?: let {
            Timber.e("No track to display.")
        }

        playlist.playingGameId?.let {
            displayGame(it, true, false)
        }

        displayState(player.state)

        displayPosition(player.playbackTimePosition)
    }

    private fun displayGame(gameId: String?, force: Boolean, animate: Boolean) {
        if (gameId != null) {
            val game = repository.getGameSync(gameId)

            if (force || this.game !== game) {
                view?.setGameBoxArt(game?.artLocal, !force)
                view?.setGameTitle(game?.title ?: RealmRepository.GAME_UNKNOWN, animate)
            }

            this.game = game
        }
    }

    private fun displayTrack(trackId: String?, animate: Boolean) {
        if (trackId != null && trackId != track?.id) {
            val track = repository.getTrackSync(trackId)

            if (track != null) {

                this.track = track

                view?.setTrackTitle(track.title.orEmpty(), animate)
                view?.setArtist(track.artistText.orEmpty(), animate)
                view?.setTrackLength(getTimeStringFromMillis(track.trackLength ?: 0), animate)

                displayPosition(0)
            } else {
                Timber.e("Cannot load track with id %s", trackId)
            }
        }
    }

    private fun displayPosition(millisPlayed: Long) {
        if (!seekbarTouched) {
            val percentPlayed = 100 * millisPlayed / (track?.trackLength ?: 100)
            view?.setProgress(percentPlayed.toInt())

            displayTimeString(millisPlayed)
        }
    }

    private fun displayTimeString(millisPlayed: Long) {
        val timeString = getTimeStringFromMillis(millisPlayed)
        view?.setTimeElapsed(timeString)
    }

    private fun displayState(state: Int) {
        when (state) {
            PlaybackState.STATE_STOPPED -> {
                displayPosition(0)
            }
        }
    }
}