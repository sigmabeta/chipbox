package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.MainModule
import net.sigmabeta.chipbox.view.activity.MainActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(MainModule::class)
)
interface MainComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: MainActivity)
}