package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.GameModule
import net.sigmabeta.chipbox.view.activity.GameActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(GameModule::class)
)
interface GameComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: GameActivity)
}