package net.sigmabeta.chipbox.ui.artist

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class ArtistListPresenter @Inject constructor() : FragmentPresenter() {
    var view: ArtistListView? = null

    var artists: MutableList<Artist>? = null

    fun onItemClick(id: Long) {
        view?.launchNavActivity(id)
    }

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
                showEmptyState()
            }
        } ?: let {
            showEmptyState()
        }
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> view?.showFilesScreen()
        }
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is ArtistListView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun setupHelper() {
        view?.showLoadingSpinner()
        view?.hideEmptyState()

        val subscription = Artist.getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            artists = it

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


        subscriptions.add(subscription)
    }

    private fun showContent(artists: MutableList<Artist>) {
        view?.setArtists(artists)
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