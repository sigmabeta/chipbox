package net.sigmabeta.chipbox.ui.playlist

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PlaylistFragmentPresenter @Inject constructor(val player: Player,
                                                    val playlist: Playlist,
                                                    val updater: UiUpdater) : FragmentPresenter<PlaylistFragmentView>() {
    var trackList: MutableList<Track>? = null

    var queuePosition: Int? = null

    var oldQueuePosition = -1

    /**
     * Public Methods
     */

    fun onItemClick(position: Int) {
        player.play(position)
    }

    fun onTrackMoved(originPos: Int, destPos: Int) {
        Collections.swap(trackList, originPos, destPos)
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
        trackList?.let {
            it.removeAt(position)
            playlist.onTrackRemoved(position)
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
        state = UiState.READY
    }

    override fun teardown() = Unit

    override fun showReadyState() {
        // TODO Get tracks, we only have IDs here
        val trackIdsList = playlist.playbackQueue
        repository.getTracksFromIds(trackIdsList)
                .subscribe(
                        {
                            displayTracks(it.toMutableList())
                            displayPositionHelper(false)
                        },
                        {
                            Timber.e("Unable to load playlist: %s", it.message)
                        }
                )

        val subscription = updater.asObservable()
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

    override fun onClick(id: Int) = Unit

    /**
     * Private Methods
     */

    private fun displayTracks(playlist: MutableList<Track>) {
        this.trackList = playlist

        view?.showQueue(playlist)
    }

    private fun displayPosition(position: Int, animate: Boolean) {
        this.queuePosition = position

        view?.updatePosition(position, oldQueuePosition)
        oldQueuePosition = -1

        view?.scrollToPosition(position, animate)
    }

    private fun displayPositionHelper(animate: Boolean) {
        val position = playlist.actualPlaybackQueuePosition

        displayPosition(position, animate)
    }
}