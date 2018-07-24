package net.sigmabeta.chipbox.ui.main

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import net.sigmabeta.chipbox.backend.PrefManager
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ChromePresenter
import net.sigmabeta.chipbox.ui.onboarding.OnboardingActivity.Companion.REQUEST_ONBOARDING
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainPresenter @Inject constructor(val prefManager: PrefManager,
                                        player: Player,
                                        scanner: LibraryScanner,
                                        playlist: Playlist,
                                        updater: UiUpdater) : ChromePresenter<MainView>(player, scanner, playlist, updater) {
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ONBOARDING) {
            if (resultCode == RESULT_OK) {
                view?.startScanner()
            } else {
                view?.finish()
            }
        }
    }

    override fun setup(arguments: Bundle?) {
        super.setup(arguments)

        if (!prefManager.get(PrefManager.KEY_ONBOARDED)) {
            view?.launchFirstOnboarding()
        }
    }
}