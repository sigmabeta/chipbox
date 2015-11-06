package net.sigmabeta.chipbox.presenter.module

import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.ArtistListView

@Module
class ArtistListModule(val view: ArtistListView) {
    @Provides @FragmentScoped fun provideArtistListView(): ArtistListView {
        logVerbose("[ArtistListModule] Providing ArtistListView...")
        return view
    }
}
