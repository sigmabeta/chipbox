package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player) : FragmentPresenter<TrackListView>() {
    var artistId: String? = null

    var artist: Artist? = null

    var tracks: List<Track>? = null

    fun onItemClick(position: Int) {
        getTrackIdList()?.let {
            player.play(it.toMutableList(), position)
        }
    }

    fun refresh(arguments: Bundle) = setupHelper(arguments)

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        setupHelper(arguments)
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (tracks == null) {
            setupHelper(arguments)
        }
    }

    override fun teardown() {
        artistId = null
        tracks = null
    }

    override fun updateViewState() {
        tracks?.let {
            if (it.isNotEmpty()) {
                showContent(it)
            } else {
                showEmptyState()
            }
        }
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> view?.showFilesScreen()
        }
    }

    private fun setupHelper(arguments: Bundle?) {
        loading = true

        artistId = arguments?.getString(TrackListFragment.ARGUMENT_ARTIST)

        artistId?.let {
            val artistLoad = repository.getArtist(it)
                    .subscribe(
                            {
                                this.artist = it
                                view?.setActivityTitle(it.name ?: "Unknown Artist")

                                tracks = it.tracks
                                tracks?.let {
                                    if (it.isNotEmpty()) {
                                        this.tracks = it
                                        showContent(it)
                                    }
                                } ?: let {
                                    logError("[SongListPresenter] Error: No tracks for artist ${this.artist?.id}")
                                }
                            },
                            {
                                logError("[SongListPresenter] Error: ${it.message}")
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(artistLoad)
        } ?: let {
            val tracksLoad = repository.getTracks()
                    .subscribe(
                            {
                                logInfo("[SongListPresenter] Loaded ${it.size} tracks.")

                                tracks = it

                                if (it.isNotEmpty()) {
                                    showContent(it)
                                } else {
                                    showEmptyState()
                                }
                            },
                            {
                                showEmptyState()
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(tracksLoad)
        }
    }

    private fun showContent(it: List<Track>) {
        view?.setTracks(it)
        view?.showContent()
    }

    private fun showEmptyState() {
        view?.showEmptyState()
    }

    private fun getTrackIdList() = tracks?.map(Track::id)?.toMutableList()
}