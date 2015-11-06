package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.NavigationModule
import net.sigmabeta.chipbox.view.activity.NavigationActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(NavigationModule::class)
)
interface NavigationComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: NavigationActivity)
}