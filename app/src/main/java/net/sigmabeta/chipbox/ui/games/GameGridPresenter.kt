package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.ListPresenter
import javax.inject.Inject

@ActivityScoped
class GameGridPresenter @Inject constructor(val updater: UiUpdater) : ListPresenter<GameListView, Game, GameViewHolder>() {
    var platformName: String? = null

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        val id = list?.get(position)?.id ?: return
        view?.launchGameActivity(id, position)
    }

    override fun getLoadOperation() = platformName?.let {
        repository.getGamesForPlatform(it)
    } ?: let {
        repository.getGames()
    }

    override fun loadArguments(arguments: Bundle?) {
        platformName = arguments?.getString(GameGridFragment.ARGUMENT_PLATFORM_NAME)
//        Timber.v()
    }

    override fun showReadyState() {
        super.showReadyState()

        platformName?.let {
            view?.setTitle(it)
        }
    }

    /**
     * BasePresenter
     */

    override fun teardown() {
        platformName = null
    }
}