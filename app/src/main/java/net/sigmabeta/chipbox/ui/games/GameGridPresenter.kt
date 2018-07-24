package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ListPresenter
import javax.inject.Inject

@ActivityScoped
class GameGridPresenter @Inject constructor(val updater: UiUpdater,
                                            val scanner: LibraryScanner) : ListPresenter<GameListView, Game, GameViewHolder>() {
    var platformName: String? = null

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        if (scanner.state != LibraryScanner.STATE_SCANNING) {
            val id = list?.get(position)?.id ?: return
            view?.launchGameActivity(id, position)
        } else {
            view?.showScanningWaitMessage()
        }
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
        super.teardown()
        platformName = null
    }
}