package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.presenter.module.PlayerFragmentModule
import net.sigmabeta.chipbox.view.fragment.PlayerFragment

@FragmentScoped
@Subcomponent(
        modules = arrayOf(PlayerFragmentModule::class)
)
interface PlayerFragmentComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: PlayerFragment)
}