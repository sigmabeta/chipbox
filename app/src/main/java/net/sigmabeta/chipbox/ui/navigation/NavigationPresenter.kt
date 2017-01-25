package net.sigmabeta.chipbox.ui.navigation

import android.media.session.PlaybackState
import android.os.Bundle
import io.realm.Realm
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationPresenter @Inject constructor(val player: Player, val repository: Repository) : ActivityPresenter() {
    var view: NavigationView? = null

    // A property is kept in order to be able to track changes in state.
    var state = player.state

    var game: Game? = null

    fun onNowPlayingClicked() {
        view?.launchPlayerActivity()
    }

    fun onPlayFabClicked() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()

            PlaybackState.STATE_PAUSED -> player.play()

            PlaybackState.STATE_STOPPED -> player.play()
        }
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun setup(arguments: Bundle?) {
        val fragmentTag = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_TAG)
        val fragmentArg = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_ARG_STRING)
        val fragmentArgLong = arguments?.getLong(NavigationActivity.ARGUMENT_FRAGMENT_ARG_LONG) ?: Track.PLATFORM_ALL

        if (fragmentTag != null) {
            view?.showFragment(fragmentTag, fragmentArg, fragmentArgLong)
        }
    }

    override fun teardown() {
        state = -1
        game = null
    }

    override fun updateViewState() {
        updateHelper()

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.trackId, true)
                        is PositionEvent -> { /* no-op */ }
                        is GameEvent -> displayGame(it.gameId, false)
                        is StateEvent -> displayState(state, it.state)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) = Unit

    override fun setView(view: BaseView) {
        if (view is NavigationView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    override fun getView(): BaseView? = view

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

        this.state = newState
    }

    private fun displayTrack(trackId: String?, animate: Boolean) {
        if (trackId != null) {
            val realm = Realm.getDefaultInstance()
            val track = repository.getTrackSync(trackId)

            if (track != null) {
                view?.setTrackTitle(track.title.orEmpty(), animate)
                view?.setArtist(track.artistText.orEmpty(), animate)
            } else {
                logError("Cannot load track with id $trackId")
            }
        }
    }

    private fun updateHelper() {
        player.playingTrackId?.let {
            displayTrack(it, false)
        }

        player.playingGameId?.let {
            displayGame(it, true)
        }

        displayState(state, player.state)
    }

    private fun displayGame(gameId: String?, force: Boolean) {
        if (gameId != null) {
            val realm = Realm.getDefaultInstance()
            val game = repository.getGameSync(gameId)

            if (force || this.game != game) {
                view?.setGameBoxArt(game?.artLocal, !force)
            }

            this.game = game
        }
    }
}
