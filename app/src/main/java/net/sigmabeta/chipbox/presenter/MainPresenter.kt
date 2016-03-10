package net.sigmabeta.chipbox.presenter

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logWarning
import net.sigmabeta.chipbox.view.activity.FileListActivity
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.MainView
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class MainPresenter @Inject constructor(val player: Player) : ActivityPresenter() {
    var view: MainView? = null

    var state = player.state

    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.drawer_add_folder -> view?.launchFileListActivity()
            R.id.drawer_refresh -> view?.launchScanActivity()
        }

        return true
    }

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

    private fun displayTrack(track: Track) {
        view?.setTrackTitle(track.title)
        view?.setArtist(track.artist)
        view?.setGameBoxart(track.gameId)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == FileListActivity.REQUEST_ADD_FOLDER) {
            if (resultCode == FileListActivity.RESULT_ADD_SUCCESSFUL) {
                view?.launchScanActivity()
            }
        }
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
    }

    override fun teardown() {
    }

    override fun updateViewState() {
        player.playingTrack?.let {
            displayTrack(it)
        }

        displayState(state, player.state)

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> displayTrack(it.track)
                        is PositionEvent -> {
                            /* no-op */
                        }
                        is StateEvent -> displayState(state, it.state)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun setView(view: BaseView) {
        if (view is MainView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}
