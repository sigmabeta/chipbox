package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.ScanView

@Module
class ScanModule(val view: ScanView) {
    @Provides @ActivityScoped fun provideScanView(): ScanView {
        logVerbose("[ScanModule] Providing ScanView...")
        return view
    }
}