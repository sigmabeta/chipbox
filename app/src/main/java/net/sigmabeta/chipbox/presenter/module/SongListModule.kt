package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.SongListView

@Module
class SongListModule(val view: SongListView) {
    @Provides @FragmentScoped fun provideSongListView(): SongListView {
        logVerbose("[SongListModule] Providing SongListView...")
        return view
    }
}