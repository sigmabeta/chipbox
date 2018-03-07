package net.sigmabeta.chipbox.ui.artist

import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import javax.inject.Inject

@ActivityScoped
class ArtistListPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<ArtistListView>() {
    var artists: List<Artist>? = null

    private var scannerSubscription: Disposable? = null

    fun onItemClick(position: Int) {
        val id = artists?.get(position)?.id ?: return
        view?.launchNavActivity(id)
    }

    fun refresh() = setupHelper()

    override fun setup(arguments: Bundle?) {
        setupHelper()
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (artists == null) {
            setupHelper()
        }
    }

    override fun teardown() {
        artists = null
    }

    override fun showReadyState() {
        view?.setArtists(artists!!)
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

    private fun setupHelper() {
        state = UiState.LOADING

        loadArtists()
    }

    private fun loadArtists() {
        val subscription = repository.getArtists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("Artists Loaded")

                            artists = it

                            if (it.isNotEmpty()) {
                                state = UiState.READY
                            } else {
                                state = UiState.EMPTY
                            }
                        },
                        {
                            state = UiState.ERROR

                            view?.showEmptyState()
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )


        subscriptions.add(subscription)
        listenForFileScans()
    }

    // TODO Move into a "Top level presenter" superclass
    private fun listenForFileScans() {
        if (scannerSubscription?.isDisposed == false) {
            scannerSubscription?.dispose()
        }

        scannerSubscription = updater.asFlowable()
                .filter { it is FileScanCompleteEvent }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    loadArtists()
                }

        subscriptions.add(scannerSubscription!!)
    }
}