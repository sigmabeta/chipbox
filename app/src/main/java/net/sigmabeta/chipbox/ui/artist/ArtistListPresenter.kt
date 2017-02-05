package net.sigmabeta.chipbox.ui.artist

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject

@ActivityScoped
class ArtistListPresenter @Inject constructor() : FragmentPresenter<ArtistListView>() {
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
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> view?.showFilesScreen()
        }
    }

    private fun setupHelper() {
        loading = true

        val subscription = repository.getArtists()
                .subscribe(
                        {
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