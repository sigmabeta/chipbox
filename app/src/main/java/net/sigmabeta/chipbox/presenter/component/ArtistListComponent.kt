package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.presenter.module.ArtistListModule
import net.sigmabeta.chipbox.view.fragment.ArtistListFragment

@FragmentScoped
@Subcomponent(
        modules = arrayOf(ArtistListModule::class)
)
interface ArtistListComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: ArtistListFragment)
}