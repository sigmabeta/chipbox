package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView
import javax.inject.Inject

@ActivityScoped
class PlayerActivityPresenter @Inject constructor(val view: PlayerActivityView) {
    fun onCreate() {
        view.showPlayerFragment()
    }
}