package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.PlatformListView

@Module
class PlatformListModule(val view: PlatformListView) {
    @Provides @FragmentScoped fun providePlatformListView(): PlatformListView {
        logVerbose("[PlatformListModule] Providing PlatformListView...")
        return view
    }
}