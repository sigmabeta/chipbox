package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView

@Module
class PlayerActivityModule(val view: PlayerActivityView) {
    @Provides @ActivityScoped fun providePlayerView(): PlayerActivityView {
        logVerbose("[PlayerModule] Providing PlayerView...")
        return view
    }
}
