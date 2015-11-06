package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.presenter.module.PlatformListModule
import net.sigmabeta.chipbox.view.fragment.PlatformListFragment

@FragmentScoped
@Subcomponent(
        modules = arrayOf(PlatformListModule::class)
)
interface PlatformListComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: PlatformListFragment)
}