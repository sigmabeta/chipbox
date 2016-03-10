package net.sigmabeta.chipbox.presenter

import android.database.Cursor
import android.os.Bundle
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import net.sigmabeta.chipbox.view.interfaces.BaseView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@FragmentScoped
class ArtistListPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: ArtistListView? = null

    var artists: Cursor? = null

    override fun setup(arguments: Bundle?) {
        val subscription = database.getArtistList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            artists = it
                            view?.setCursor(it)
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
        val cursor = artists
        if (cursor != null) {
            view?.setCursor(cursor)
        }
    }

    override fun setView(view: BaseView) {
        if (view is ArtistListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}