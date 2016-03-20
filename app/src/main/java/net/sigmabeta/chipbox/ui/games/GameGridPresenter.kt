package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@ActivityScoped
class GameGridPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: GameListView? = null

    var platform = Track.PLATFORM_ALL

    var games: List<Game>? = null

    fun onItemClick(id: Long) {
        view?.launchGameActivity(id)
    }

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        platform = arguments?.getLong(GameGridFragment.ARGUMENT_PLATFORM_INDEX) ?: Track.PLATFORM_UNDEFINED

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

        val titleResource = when (platform) {
            Track.PLATFORM_32X -> R.string.platform_name_32x
            Track.PLATFORM_GAMEBOY -> R.string.platform_name_gameboy
            Track.PLATFORM_GENESIS -> R.string.platform_name_genesis
            Track.PLATFORM_NES -> R.string.platform_name_nes
            Track.PLATFORM_SNES -> R.string.platform_name_snes
            else -> -1
        }

        if (titleResource != -1) {
            view?.setActivityTitle(titleResource)
        }

        view?.clearClickedViewHolder()
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is GameListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}