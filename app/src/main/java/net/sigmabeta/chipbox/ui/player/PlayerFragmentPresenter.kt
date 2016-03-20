package net.sigmabeta.chipbox.ui.player

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.getTimeStringFromMillis
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class PlayerFragmentPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: PlayerFragmentView? = null

    var game: Game? = null

    var track: Track? = null

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

        player.playingGame?.let {
            displayGame(it, true)
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
                        is GameEvent -> displayGame(it.game, false)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlayerFragmentView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun displayGame(game: Game?, force: Boolean) {
        if (force || this.game != game) {
            view?.setGameBoxArt(game?.artLocal, !force)
        }

        this.game = game
    }

    private fun displayTrack(track: Track) {
        this.track = track

        view?.setTrackTitle(track.title.orEmpty())
        view?.setArtist(track.artist.orEmpty())
        view?.setGameTitle(track.gameTitle.orEmpty())
        view?.setTrackLength(getTimeStringFromMillis(track.trackLength ?: 0))

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