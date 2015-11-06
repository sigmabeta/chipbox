package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.presenter.module.SongListModule
import net.sigmabeta.chipbox.view.fragment.SongListFragment

@FragmentScoped
@Subcomponent(
        modules = arrayOf(SongListModule::class)
)
interface SongListComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: SongListFragment)
}