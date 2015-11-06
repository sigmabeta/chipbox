package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.MainView

@Module
class MainModule(val view: MainView) {
    @Provides @ActivityScoped fun provideMainView(): MainView {
        logVerbose("[MainModule] Providing MainView...")
        return view
    }
}