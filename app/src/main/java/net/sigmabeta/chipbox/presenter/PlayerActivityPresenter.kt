package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView
import javax.inject.Inject

@ActivityScoped
class PlayerActivityPresenter @Inject constructor(val view: PlayerActivityView) {
    fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            view.showPlayerFragment()
        }
    }
}