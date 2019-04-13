package net.sigmabeta.chipbox.ui.player

import android.media.session.PlaybackState
import android.os.Bundle
import android.widget.SeekBar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerActivityPresenter @Inject constructor(val player: Player,
                                                  val playlist: Playlist,
                                                  val updater: UiUpdater) : ActivityPresenter<PlayerActivityView>(),
        SeekBar.OnSeekBarChangeListener {
    var game: Game? = null

    var track: Track? = null

    var seekbarTouched = false

    /**
     * OnSeekbarChangeListener
     */

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        val length = track?.trackLength ?: 0
        val seekPosition = (length * progress / 100)

        displayTimeString(seekPosition)    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        seekbarTouched = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        val length = track?.trackLength ?: 0
        val seekPosition = (length * (seekBar?.progress ?: 0) / 100)
        player.seek(seekPosition)

        // TODO This is a hack
        Observable.just(1)
                .delay(66L, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe {
                    seekbarTouched = false
                }
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_fab -> view?.showPlaylistScreen()
            R.id.button_play -> onPlayPauseClick()
            R.id.button_skip_forward -> player.skipToNext()
            R.id.button_skip_back -> player.skipToPrev()
            R.id.button_shuffle -> toggleShuffle()
            R.id.button_repeat -> toggleRepeat()
            else -> handleError(NotImplementedError("This button is not implemented yet"))
        }
    }

    override fun setup(arguments: Bundle?) {
        if (state == UiState.CANCELED) {
            state = UiState.READY
        } else {
            state = UiState.CANCELED
        }
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (playlist.playingTrackId == null && playlist.playbackQueue.isEmpty()) {
            view?.finish()
        }
    }

    override fun onTempDestroy() = Unit

    override fun teardown() {
        track = null
    }

    override fun showReadyState() {
        updateHelper()
        
        playlist.playingTrackId?.let {
            displayTrack(it, true, false)
        } ?: let {
            Timber.e("No track to display.")
        }

        playlist.playingGameId?.let {
            displayGame(it, true, false)
        }

        displayState(player.state)
        displayPosition(player.playbackTimePosition)

        val subscription = updater.asFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.trackId, false, true)
                        is PositionEvent -> displayPosition(it.millisPlayed)
                        is StateEvent -> displayState(it.state)
                        is GameEvent -> displayGame(it.gameId, false, true)
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onReenter() = Unit

    /**
     * Private Methods
     */

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

    private fun displayTrack(trackId: String?, force: Boolean, animate: Boolean) {
        if (trackId != null && (trackId != track?.id || force)) {
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
            view?.setSeekProgress(percentPlayed.toInt())

            displayTimeString(millisPlayed)
        }
    }

    private fun displayTimeString(millisPlayed: Long) {
        val timeString = getTimeStringFromMillis(millisPlayed)
        view?.setTimeElapsed(timeString)
    }

    private fun displayState(state: Int) {
        when (state) {
            PlaybackState.STATE_PLAYING -> view?.showPauseButton()
            PlaybackState.STATE_PAUSED -> view?.showPlayButton()
            PlaybackState.STATE_STOPPED -> {
                displayPosition(0)
                view?.showPlayButton()
            }
        }
    }

    private fun onPlayPauseClick() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()
            PlaybackState.STATE_PAUSED -> player.start(null)
            PlaybackState.STATE_STOPPED -> player.start(null)
        }
    }

    private fun toggleShuffle() {
        playlist.shuffle = !playlist.shuffle

        displayShuffle()
    }

    private fun toggleRepeat() {
        playlist.repeat = if (playlist.repeat >= Player.REPEAT_ONE) {
            Player.REPEAT_OFF
        } else {
            playlist.repeat + 1
        }

        displayRepeat()
    }

    private fun updateHelper() {
        displayShuffle()

        displayRepeat()

        displayState(player.state)
    }

    private fun displayShuffle() {
        if (playlist.shuffle) {
            view?.setShuffleEnabled()
        } else {
            view?.setShuffleDisabled()
        }
    }

    private fun displayRepeat() {
        when (playlist.repeat) {
            Player.REPEAT_OFF -> view?.setRepeatDisabled()
            Player.REPEAT_ALL -> view?.setRepeatAll()
            Player.REPEAT_ONE -> view?.setRepeatOne()
            Player.REPEAT_INFINITE -> view?.setRepeatInfinite()
            else -> handleError(IllegalStateException("Unimplemented player state."))
        }
    }
}