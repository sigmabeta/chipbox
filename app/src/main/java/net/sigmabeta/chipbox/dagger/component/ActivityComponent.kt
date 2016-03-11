package net.sigmabeta.chipbox.dagger.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.view.activity.*

@ActivityScoped
@Subcomponent
interface ActivityComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: MainActivity)

    fun inject(view: NavigationActivity)
    fun inject(view: PlayerActivity)
    fun inject(view: ScanActivity)
    fun inject(view: FileListActivity)
    fun inject(view: GameActivity)
}