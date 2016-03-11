package net.sigmabeta.chipbox.presenter

import android.database.Cursor
import android.os.Bundle
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.fragment.GameGridFragment
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.GameListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@FragmentScoped
class GameGridPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: GameListView? = null

    var platform = Track.PLATFORM_ALL

    var games: Cursor? = null

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        platform = arguments?.getInt(GameGridFragment.ARGUMENT_PLATFORM_INDEX) ?: Track.PLATFORM_UNDEFINED

        val subscription = database.getGamesList(platform)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            games = it
                            view?.setCursor(it)
                        }
                )

        subscriptions.add(subscription)
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun teardown() {
        games = null
        platform = Track.PLATFORM_UNDEFINED
    }

    override fun updateViewState() {
        val cursor = games
        if (cursor != null) {
            view?.setCursor(cursor)
        }
    }

    override fun setView(view: BaseView) {
        if (view is GameListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}