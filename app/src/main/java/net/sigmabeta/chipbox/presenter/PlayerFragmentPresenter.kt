package net.sigmabeta.chipbox.presenter

import android.media.session.PlaybackState
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@FragmentScoped
class PlayerFragmentPresenter @Inject constructor(val view: PlayerFragmentView,
                                                  val database: SongDatabaseHelper,
                                                  val player: Player) {
    var track: Track? = null
    var state = player.state

    var subscription: Subscription? = null

    fun onCreate() { }

    fun onViewCreated() {
        val localTrack = player.playingTrack

        if (localTrack != null) {
            displayTrack(localTrack)
        } else {
            logError("[PlayerFragmentPresenter] No track to display.")
        }

        displayState(state)
    }

    fun onFabClick() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()

            PlaybackState.STATE_PAUSED -> player.play()

            PlaybackState.STATE_STOPPED -> {
                val localTrack = track

                if (localTrack != null) {
                    player.play(localTrack)
                }
            }
        }
    }

    fun onResume() {
        subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.track)
                        is PositionEvent -> displayPosition(it.millisPlayed)
                        is StateEvent -> displayState(it.state)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }
    }

    fun onPause() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun displayTrack(track: Track) {
        this.track = track

        view.setTrackTitle(track.title)
        view.setArtist(track.artist)
        view.setGameTitle(track.gameTitle)
        view.setGameBoxart(track.gameId)

        displayPosition(0)
    }

    private fun displayPosition(millisPlayed: Int) {
        val millisAsLong = millisPlayed.toLong()

        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisAsLong)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millisAsLong)
        val displaySeconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)

        val timeString = "%d:%02d".format(minutes, displaySeconds)

        view.setTimeElapsed(timeString)
    }

    private fun displayState(state: Int) {
        this.state = state

        when (state) {
            PlaybackState.STATE_PLAYING -> view.showPauseButton()

            PlaybackState.STATE_PAUSED -> view.showPlayButton()

            PlaybackState.STATE_STOPPED -> {
                view.showPlayButton()
                displayPosition(0)
            }
        }
    }
}