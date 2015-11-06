package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.presenter.module.GameListModule
import net.sigmabeta.chipbox.view.fragment.GameGridFragment

@FragmentScoped
@Subcomponent(
        modules = arrayOf(GameListModule::class)
)
interface GameListComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: GameGridFragment)
}
