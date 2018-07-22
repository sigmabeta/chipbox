package net.sigmabeta.chipbox.ui.playlist

import io.reactivex.android.schedulers.AndroidSchedulers
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ListPresenter
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PlaylistFragmentPresenter @Inject constructor(val player: Player,
                                                    val playlist: Playlist,
                                                    val updater: UiUpdater) : ListPresenter<PlaylistFragmentView, Track, PlaylistTrackViewHolder>() {
    var queuePosition: Int? = null

    var oldQueuePosition = -1

    fun onTrackMoved(originPos: Int, destPos: Int) {
        Collections.swap(list, originPos, destPos)
        playlist.onTrackMoved(originPos, destPos)
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
        (list as MutableList<Track>).let {
            it.removeAt(position)
            playlist.onTrackRemoved(position)
            view?.onTrackRemoved(position)
        }
    }

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        player.play(position)
    }

    override fun getLoadOperation() = null

    override fun getLoadOperationWithoutDiffs() = repository.getTracksFromIds(playlist.playbackQueue)

    /**
     * BasePresenter
     */

    override fun teardown() {
        queuePosition = null
        oldQueuePosition = -1
    }

    override fun showReadyState() {
        super.showReadyState()

        val subscription = updater.asFlowable()
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

    private fun displayPositionHelper(animate: Boolean) {
        val position = playlist.actualPlaybackQueuePosition

        queuePosition = position
        view?.updatePosition(position, oldQueuePosition)
        oldQueuePosition = -1
        view?.scrollToPosition(position, animate)
    }
}