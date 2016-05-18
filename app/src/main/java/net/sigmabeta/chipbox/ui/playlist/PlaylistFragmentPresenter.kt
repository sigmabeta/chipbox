package net.sigmabeta.chipbox.ui.playlist

import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PlaylistFragmentPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: PlaylistFragmentView? = null

    var playlist: MutableList<Track>? = null

    var queuePosition: Int? = null

    var oldQueuePosition = -1

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

        if (originPos == queuePosition) {
            queuePosition = destPos
            oldQueuePosition = destPos
        } else if (destPos == queuePosition) {
            queuePosition = originPos
            oldQueuePosition = originPos
        }
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

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
    }

    override fun teardown() {
    }

    override fun updateViewState() {
        displayTracks(player.playbackQueue)

        displayPositionHelper(false)

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> {
                            displayPositionHelper(true)
                        }
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

    private fun displayPosition(position: Int, animate: Boolean) {
        this.queuePosition = position

        view?.updatePosition(position, oldQueuePosition)
        oldQueuePosition = -1

        view?.scrollToPosition(position, animate)
    }

    private fun displayPositionHelper(animate: Boolean) {
        val position = player.actualPlaybackQueuePosition

        displayPosition(position, animate)
    }
}