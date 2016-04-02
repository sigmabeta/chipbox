package net.sigmabeta.chipbox.ui.playlist

import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PlaylistFragmentPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: PlaylistFragmentView? = null

    var playlist: MutableList<Track>? = null

    /**
     * Public Methods
     */

    fun onItemClick(position: Long) {
        player.play(position.toInt())
    }

    fun onTrackMoved(originPos: Int, destPos: Int) {
        Collections.swap(playlist, originPos, destPos)
        player.onTrackMoved(originPos, destPos)
        view?.onTrackMoved(originPos, destPos)
    }

    fun onTrackRemoved(position: Int) {
        playlist?.let {
            it.removeAt(position)
            player.onTrackRemoved(position)
            view?.onTrackRemoved(position)
        }
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(savedInstanceState: Bundle) = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
    }

    override fun teardown() {
    }

    override fun updateViewState() {
        player.playbackQueue?.let {
            displayTracks(it)
        }

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> view?.updatePosition(player.playbackQueuePosition)
                        else -> logWarning("[PlaylistFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlaylistFragmentView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */

    private fun displayTracks(playlist: MutableList<Track>) {
        this.playlist = playlist

        view?.showQueue(playlist)
    }
}