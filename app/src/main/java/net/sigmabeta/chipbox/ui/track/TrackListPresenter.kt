package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import io.reactivex.Observable
import io.realm.RealmResults
import io.realm.rx.CollectionChange
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.ListPresenter
import net.sigmabeta.chipbox.ui.UiState
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player,
                                             val updater: UiUpdater) : ListPresenter<TrackListView, Track, TrackViewHolder>() {
    var artistId: String? = null

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        getTrackIdList()?.let {
            player.play(it.toMutableList(), position)
        }
    }

    // TODO Fix realm track list query problem
    override fun getLoadOperation(): Observable<CollectionChange<RealmResults<Track>>> = artistId?.let {
        null
    } ?: let {
        repository.getTracks()
    }

    override fun loadArguments(arguments: Bundle?) {
        artistId = arguments?.getString(TrackListFragment.ARGUMENT_ARTIST)
    }

    override fun showReadyState() {
        super.showReadyState()

        artistId?.let {
            Timber.w("Artist id $it")
            val disposable = repository.getArtist(it)
                    .subscribe(
                            {
                                it.name?.let { name ->
                                    view?.setActivityTitle(name)
                                }

                                printBenchmark("items Loaded")

                                list = it.tracks

                                if (list?.isNotEmpty() == true) {
                                    Timber.v("Showing items.")
                                    state = UiState.READY
                                } else {
                                    if (it.isLoaded) {
                                        Timber.v("No items to show.")
                                        state = UiState.EMPTY
                                    } else {
                                        Timber.v("Query not actually ready yet.")
                                    }
                                }
                            },
                            {
                                state = UiState.ERROR
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(disposable)
        }
    }

    /**
     * BasePresenter
     */

    override fun teardown() {
        artistId = null
    }

    /**
     * Implementation Details
     */

    private fun getTrackIdList() = list
            ?.map(Track::id)
            ?.toMutableList()
}