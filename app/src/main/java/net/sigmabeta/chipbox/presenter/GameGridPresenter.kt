package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.GameListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

class GameGridPresenter @Inject constructor(val view: GameListView,
                                            val database: SongDatabaseHelper) {
    var platform = Track.PLATFORM_ALL

    fun onCreate(platform: Int) {
        this.platform = platform
    }

    fun onCreateView() {
        database.getGamesList(platform)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            view.setCursor(it)
                        }
                )
    }
}