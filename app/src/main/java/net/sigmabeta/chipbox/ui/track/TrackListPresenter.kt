package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player, val repository: Repository) : FragmentPresenter() {
    var view: TrackListView? = null

    var artistId: String? = null

    var artist: Artist? = null

    var tracks: List<Track>? = null

    fun onItemClick(position: Int) {
        tracks?.let {
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
            if (it.size > 0) {
                showContent(it)
            } else {
                showEmptyState()
            }
        } ?: let {
            view?.showLoadingSpinner()
        }
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> view?.showFilesScreen()
        }
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is TrackListView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun setupHelper(arguments: Bundle?) {
        artistId = arguments?.getString(TrackListFragment.ARGUMENT_ARTIST)

        view?.showLoadingSpinner()
        view?.hideEmptyState()

        artistId?.let {
            val artistLoad = repository.getArtist(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                this.artist = it
                                view?.setActivityTitle(it.name ?: "Unknown Artist")

                                // TODO This really, really needs to be async
                                tracks?.let {
                                    if (it.isNotEmpty()) {
                                        this.tracks = it
                                        showContent(it)
                                    }
                                } ?: let {
                                    logError("[SongListPresenter] Error: No tracks for artist ${this.artist?.id}")
                                    view?.onTrackLoadError()
                                }
                            },
                            {
                                view?.onTrackLoadError()
                                logError("[SongListPresenter] Error: ${it.message}")
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(artistLoad)
        } ?: let {
            val tracksLoad = repository.getTracks()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                logInfo("[SongListPresenter] Loaded ${it.size} tracks.")

                                tracks = it

                                if (it.size > 0) {
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
        view?.hideLoadingSpinner()
        view?.hideEmptyState()
        view?.showContent()
    }

    private fun showEmptyState() {
        view?.hideLoadingSpinner()
        view?.hideContent()
        view?.showEmptyState()
    }
}