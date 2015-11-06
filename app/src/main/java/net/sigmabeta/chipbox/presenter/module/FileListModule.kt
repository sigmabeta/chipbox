package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.FileListView

@Module
class FileListModule(val view: FileListView) {
    @Provides @ActivityScoped fun provideFileListView(): FileListView {
        logVerbose("[FileListModule] Providing FileListView...")
        return view
    }
}
