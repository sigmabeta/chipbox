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

    fun onClick(id: Int) {
        when (id) {
            R.id.button_play -> onPlayPauseClick()
            R.id.button_skip_forward -> player.skipToNext()
            R.id.button_skip_back -> player.skipToPrev()
            else -> view?.showToastMessage("Unimplemented.")
        }
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(savedInstanceState: Bundle) = Unit

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

    private fun updateHelper() {
        displayState(player.state)

        if (elevated) {
            view?.elevate()
        } else {
            view?.unElevate()
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
