package net.sigmabeta.chipbox.ui.player

import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class PlayerControlsPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: PlayerControlsView? = null

    var updatedOnce = false

    var elevated = false

    /**
     * Public Methods
     */

    fun onPlaylistShown() {
        view?.elevate()
        elevated = true
    }

    fun onPlaylistHidden() {
        view?.unElevate()
        elevated = false
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_play -> onPlayPauseClick()
            R.id.button_skip_forward -> player.skipToNext()
            R.id.button_skip_back -> player.skipToPrev()
            R.id.button_shuffle -> toggleShuffle()
            R.id.button_repeat -> toggleRepeat()
            else -> view?.showToastMessage("Unimplemented.")
        }
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (player.playingTrackId == null && player.playbackQueue.isEmpty()) {
            view?.finish()
        }
    }

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) = Unit

    override fun teardown() = Unit

    override fun updateViewState() {
        updateHelper()

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is StateEvent -> displayState(it.state)
                    }
                }

        subscriptions.add(subscription)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlayerControlsView) this.view = view
    }

    override fun clearView() {
        view = null
        updatedOnce = false
        elevated = false
    }

    /**
     * Private Methods
     */

    private fun onPlayPauseClick() {
        when (player.state) {
            PlaybackState.STATE_PLAYING -> player.pause()
            PlaybackState.STATE_PAUSED -> player.play()
            PlaybackState.STATE_STOPPED -> player.play()
        }
    }

    private fun toggleShuffle() {
        player.shuffle = !player.shuffle

        displayShuffle()
    }

    private fun toggleRepeat() {
        player.repeat = if (player.repeat >= Player.REPEAT_ONE) {
            Player.REPEAT_OFF
        } else {
            player.repeat + 1
        }

        displayRepeat()
    }

    private fun updateHelper() {
        displayShuffle()

        displayRepeat()

        displayState(player.state)

        if (updatedOnce) {
            if (elevated) {
                view?.elevate()
            } else {
                view?.unElevate()
            }
        }

        updatedOnce = true
    }

    private fun displayShuffle() {
        if (player.shuffle) {
            view?.setShuffleEnabled()
        } else {
            view?.setShuffleDisabled()
        }
    }

    private fun displayRepeat() {
        when (player.repeat) {
            Player.REPEAT_OFF -> view?.setRepeatDisabled()
            Player.REPEAT_ALL -> view?.setRepeatAll()
            Player.REPEAT_ONE -> view?.setRepeatOne()
            Player.REPEAT_INFINITE -> view?.setRepeatInfinite()
            else -> view?.showErrorSnackbar("Unimplemented state.", null, null)
        }
    }

    private fun displayState(state: Int) {
        when (state) {
            PlaybackState.STATE_PLAYING -> view?.showPauseButton()
            PlaybackState.STATE_PAUSED -> view?.showPlayButton()
            PlaybackState.STATE_STOPPED -> view?.showPlayButton()
        }
    }
}
