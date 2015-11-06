package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.GameListView

@Module
class GameListModule(val view: GameListView) {
    @Provides @FragmentScoped fun provideGameListView(): GameListView {
        logVerbose("[GameListModule] Providing GameListView...")
        return view
    }
}