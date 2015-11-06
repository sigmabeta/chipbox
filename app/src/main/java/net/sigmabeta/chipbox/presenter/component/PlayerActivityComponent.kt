package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.PlayerActivityModule
import net.sigmabeta.chipbox.view.activity.PlayerActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(PlayerActivityModule::class)
)
interface PlayerActivityComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: PlayerActivity)
}