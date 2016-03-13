package net.sigmabeta.chipbox.ui.player

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.player.PlayerFragmentView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class PlayerFragmentPresenter @Inject constructor(val player: Player,
                                                  val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: PlayerFragmentView? = null

    var gameId: Long = -1L
        set (value) {
            if (field != value) {
                field = value
                loadGame(value)
            }
        }

    var game: Game? = null

    var track: Track? = null
        set (value) {
            field = value
            gameId = value?.gameId ?: -1L
        }

    var state: Int? = null

    var seekbarTouched = false

    fun onFabClick() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()

            PlaybackState.STATE_PAUSED -> player.play()

            PlaybackState.STATE_STOPPED -> player.play()
        }
    }

    fun onNextClick() {
        player.skipToNext()
    }

    fun onRewindClick() {
        player.skipToPrev()
    }

    fun onSeekbarTouch() {
        seekbarTouched = true
    }

    fun onSeekbarRelease(progress: Int) {
        player.seek(progress)
        seekbarTouched = false
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun setup(arguments: Bundle?) {
    }

    override fun teardown() {
        track = null
        state = null
        seekbarTouched = false
    }

    override fun updateViewState() {
        player.playingTrack?.let {
            displayTrack(it)
        } ?: let {
            logError("[PlayerFragmentPresenter] No track to display.")
        }

        game?.let {
            displayArt(it)
        }

        displayState(player.state)

        displayPosition(player.playbackTimePosition)

        player.playbackTimePosition
        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.track)
                        is PositionEvent -> displayPosition(it.millisPlayed)
                        is StateEvent -> displayState(it.state)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun setView(view: BaseView) {
        if (view is PlayerFragmentView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun loadGame(id: Long) {
        val gameLoader = database.getGame(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe (
                        {
                            game = it
                            displayArt(it)
                        },
                        {
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )

        subscriptions.add(gameLoader)
    }

    private fun displayArt(game: Game) {
        view?.setGameBoxart(game.artLocal)
    }

    private fun displayTrack(track: Track) {
        this.track = track

        view?.setTrackTitle(track.title)
        view?.setArtist(track.artist)
        view?.setGameTitle(track.gameTitle)
        view?.setTrackLength(getTimeStringFromMillis(track.trackLength))

        displayPosition(0)
    }

    private fun displayPosition(millisPlayed: Long) {
        if (!seekbarTouched) {
            val percentPlayed = 100 * millisPlayed / (track?.trackLength ?: 100)
            view?.setProgress(percentPlayed.toInt())
        }

        val timeString = getTimeStringFromMillis(millisPlayed)
        view?.setTimeElapsed(timeString)

        view?.setUnderrunCount("Underruns ${player.stats.underrunCount}")
    }

    private fun displayState(state: Int) {
        this.state = state

        when (state) {
            PlaybackState.STATE_PLAYING -> view?.showPauseButton()

            PlaybackState.STATE_PAUSED -> view?.showPlayButton()

            PlaybackState.STATE_STOPPED -> {
                view?.showPlayButton()
                displayPosition(0)
            }
        }
    }
}