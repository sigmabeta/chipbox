package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.fragment.GameGridFragment
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.GameListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import javax.inject.Inject

@FragmentScoped
class GameGridPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: GameListView? = null

    var platform = Track.PLATFORM_ALL

    var games: ArrayList<Game>? = null

    fun onItemClick(id: Long) {
        view?.launchGameActivity(id)
    }

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
                            view?.setGames(it)
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
        games = null
        platform = Track.PLATFORM_UNDEFINED
    }

    override fun updateViewState() {
        games?.let {
            view?.setGames(it)
        }
    }

    override fun setView(view: BaseView) {
        if (view is GameListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}