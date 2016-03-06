package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.ScanModule
import net.sigmabeta.chipbox.view.activity.ScanActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(ScanModule::class)
)
interface ScanComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: ScanActivity)
}