package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.view.interfaces.ArtistListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@FragmentScoped
class ArtistListPresenter @Inject constructor(val view: ArtistListView,
                                              val database: SongDatabaseHelper) {
    fun onCreateView() {
        database.getArtistList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            view.setCursor(it)
                        }
                )
    }
}