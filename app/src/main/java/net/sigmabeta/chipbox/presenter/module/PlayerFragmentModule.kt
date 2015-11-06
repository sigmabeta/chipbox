package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView

@Module
class PlayerFragmentModule(val view: PlayerFragmentView) {
    @Provides @FragmentScoped fun providePlayerFragmentView(): PlayerFragmentView {
        logVerbose("[PlayerFragmentModule] Providing PlayerFragmentView...")
        return view
    }
}