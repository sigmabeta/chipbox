package net.sigmabeta.chipbox.presenter

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.view.interfaces.NavigationView
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject


class NavigationPresenter @Inject constructor(val view: NavigationView,
                                              val player: Player) {
    var subscription: Subscription? = null

    var state = player.state

    fun onCreate(fragmentTag: String, fragmentArg: Long, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            view.showFragment(fragmentTag, fragmentArg)
        }
    }

    fun onResume() {
        val track = player.playingTrack
        if (track != null) {
            displayTrack(track)
        }

        displayState(state, player.state)

        subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.track)
                        is PositionEvent -> { /* no-op */ }
                        is StateEvent -> displayState(state, it.state)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }
    }

    fun onPause() {
        subscription?.unsubscribe()
        subscription = null
    }

    fun onNowPlayingClicked() {
        view.launchPlayerActivity()
    }

    fun onPlayFabClicked() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()

            PlaybackState.STATE_PAUSED -> player.play()

            PlaybackState.STATE_STOPPED -> player.play()
        }
    }

    private fun displayState(oldState: Int, newState: Int) {
        when (newState) {
            PlaybackState.STATE_PLAYING -> {
                view.showPauseButton()
                view.showNowPlaying(oldState == PlaybackState.STATE_STOPPED)
            }

            PlaybackState.STATE_PAUSED -> {
                view.showPlayButton()
                view.showNowPlaying(oldState == PlaybackState.STATE_STOPPED)
            }

            PlaybackState.STATE_STOPPED -> {
                view.hideNowPlaying(oldState != PlaybackState.STATE_STOPPED)
            }
        }

        this.state = newState
    }

    private fun displayTrack(track: Track) {
        view.setTrackTitle(track.title)
        view.setArtist(track.artist)
        view.setGameBoxart(track.gameId)
    }
}
