package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.NavigationView


@Module
class NavigationModule(val view: NavigationView) {
    @Provides @ActivityScoped fun provideNavigationView(): NavigationView {
        logVerbose("[NavigationModule] Providing NavigationView...")
        return view
    }
}