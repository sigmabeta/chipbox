package net.sigmabeta.chipbox.ui.navigation

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ChromePresenter
import net.sigmabeta.chipbox.ui.UiState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationPresenter @Inject constructor(player: Player,
                                              scanner: LibraryScanner,
                                              playlist: Playlist,
                                              updater: UiUpdater) : ChromePresenter<NavigationView>(player, scanner, playlist, updater) {
    fun onUnsupportedFragment() {
        handleError(IllegalStateException("Unsupported fragment."))
    }

    override fun setup(arguments: Bundle?) {
        state = UiState.READY

        val fragmentTag = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_TAG)
        val fragmentArg = arguments?.getString(NavigationActivity.ARGUMENT_FRAGMENT_ARG_STRING)

        if (fragmentTag != null) {
            view?.showFragment(fragmentTag, fragmentArg)
        }
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit
}
