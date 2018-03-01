package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player,
                                             val updater: UiUpdater) : FragmentPresenter<TrackListView>() {
    var artistId: String? = null

    var artist: Artist? = null

    var tracks: List<Track>? = null

    private var scannerSubscription: Subscription? = null

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
        view?.setTracks(tracks!!)
        view?.showContent()

        listenForFileScans()
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
        artistId = arguments?.getString(TrackListFragment.ARGUMENT_ARTIST)

        loadTracks()
    }

    private fun loadTracks() {
        state = UiState.LOADING

        artistId?.let {
            val artistLoad = repository.getArtist(it)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                printBenchmark("Tracks Loaded")

                                this.artist = it
                                view?.setActivityTitle(it.name ?: RealmRepository.ARTIST_UNKNOWN)

                                tracks = it.tracks
                                tracks?.let { reference ->
                                    if (reference.isNotEmpty()) {
                                        this.tracks = reference
                                        state = UiState.READY
                                    } else {
                                        state = UiState.EMPTY
                                    }
                                } ?: let {
                                    state = UiState.ERROR
                                    Timber.e("Error: No tracks for artist %s", this.artist?.id)
                                }
                            },
                            {
                                Timber.e("Error: %s", it.message)
                                state = UiState.ERROR
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
                                    state = UiState.READY
                                } else {
                                    state = UiState.EMPTY
                                }
                            },
                            {
                                state = UiState.ERROR
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(tracksLoad)

            listenForFileScans()
        }
    }

    // TODO Move into a "Top level presenter" superclass
    private fun listenForFileScans() {
        if (scannerSubscription?.isUnsubscribed == false) {
            scannerSubscription?.unsubscribe()
        }

        scannerSubscription = updater.asObservable()
                .filter { it is FileScanCompleteEvent }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    loadTracks()
                }

        subscriptions.add(scannerSubscription)
    }

    private fun getTrackIdList() = tracks?.map(Track::id)?.toMutableList()
}