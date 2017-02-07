package net.sigmabeta.chipbox.ui.artist

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class ArtistListPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<ArtistListView>() {
    var artists: List<Artist>? = null

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

    override fun updateViewState() {
        artists?.let {
            if (it.size > 0) {
                showContent(it)
            } else {
                view?.showEmptyState()
            }
        }

        val subscription = updater.asObservable()
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
                        is FileScanEvent -> loadArtists()
                        is FileScanCompleteEvent -> { /* no-op */
                        }
                        is FileScanFailedEvent -> { /* no-op */
                        }
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> view?.showRescanScreen()
        }
    }

    private fun setupHelper() {
        loading = true

        loadArtists()
    }

    private fun loadArtists() {
        val subscription = repository.getArtists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("Artists Loaded")

                            loading = false
                            artists = it

                            if (it.size > 0) {
                                showContent(it)
                            } else {
                                view?.showEmptyState()
                            }
                        },
                        {
                            loading = false
                            view?.showEmptyState()
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )


        subscriptions.add(subscription)
    }

    private fun showContent(artists: List<Artist>) {
        view?.setArtists(artists)
        view?.showContent()
    }
}