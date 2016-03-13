package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Artist
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import net.sigmabeta.chipbox.view.interfaces.BaseView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import javax.inject.Inject

@FragmentScoped
class ArtistListPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: ArtistListView? = null

    var artists: ArrayList<Artist>? = null

    fun onItemClick(id: Long) {
        view?.launchNavActivity(id)
    }

    override fun setup(arguments: Bundle?) {
        val subscription = database.getArtistList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            artists = it
                            view?.setArtists(it)
                        },
                        {
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )


        subscriptions.add(subscription)
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun teardown() {
        artists = null
    }

    override fun updateViewState() {
        artists?.let {
            view?.setArtists(it)
        }
    }

    override fun setView(view: BaseView) {
        if (view is ArtistListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}