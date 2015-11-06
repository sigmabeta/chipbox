package net.sigmabeta.chipbox.presenter.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.presenter.module.FileListModule
import net.sigmabeta.chipbox.view.activity.FileListActivity

@ActivityScoped
@Subcomponent(
        modules = arrayOf(FileListModule::class)
)
interface FileListComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: FileListActivity)
}
