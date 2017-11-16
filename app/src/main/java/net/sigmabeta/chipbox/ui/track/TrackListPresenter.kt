package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player,
                                             val updater: UiUpdater) : FragmentPresenter<TrackListView>() {
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

    override fun showReadyState() {
        tracks?.let {
            if (it.isNotEmpty()) {
                showContent(it)
            } else {
                showEmptyState()
            }
        }

        val subscription = updater.asObservable()
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> { /* no-op */
                        }
                        is PositionEvent -> { /* no-op */
                        }
                        is GameEvent -> { /* no-op */
                        }
                        is StateEvent -> { /* no-op */
                        }
                        is FileScanEvent -> loadTracks()
                        is FileScanCompleteEvent -> loadTracks()
                        is FileScanFailedEvent -> { /* no-op */
                        }
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> {
                view?.startRescan()
                state = UiState.LOADING
            }
        }
    }

    private fun setupHelper(arguments: Bundle?) {
        state = UiState.LOADING

        artistId = arguments?.getString(TrackListFragment.ARGUMENT_ARTIST)

        loadTracks()
    }

    private fun loadTracks() {
        artistId?.let {
            val artistLoad = repository.getArtist(it)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                printBenchmark("Tracks Loaded")

                                this.artist = it
                                view?.setActivityTitle(it.name ?: RealmRepository.ARTIST_UNKNOWN)

                                tracks = it.tracks
                                tracks?.let {
                                    if (it.isNotEmpty()) {
                                        this.tracks = it
                                        showContent(it)
                                    }
                                } ?: let {
                                    Timber.e("Error: No tracks for artist %s", this.artist?.id)
                                }
                            },
                            {
                                Timber.e("Error: %s", it.message)
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(artistLoad)
        } ?: let {
            val tracksLoad = repository.getTracks()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                Timber.i("Loaded %s tracks.", it.size)
                                printBenchmark("Tracks Loaded")

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