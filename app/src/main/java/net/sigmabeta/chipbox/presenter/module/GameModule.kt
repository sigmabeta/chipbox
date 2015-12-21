package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.GameView

@Module
class GameModule(val view: GameView) {
    @Provides @ActivityScoped fun provideGameView(): GameView {
        logVerbose("[GameModule] Providing GameView...")
        return view
    }
}