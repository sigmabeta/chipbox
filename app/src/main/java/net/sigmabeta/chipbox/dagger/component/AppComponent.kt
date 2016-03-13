package net.sigmabeta.chipbox.dagger.component

import dagger.Component
import net.sigmabeta.chipbox.backend.PlayerService
import net.sigmabeta.chipbox.backend.module.AudioModule
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.database.module.DatabaseModule
import net.sigmabeta.chipbox.view.activity.*
import javax.inject.Singleton

@Singleton
@Component(
        modules = arrayOf(
                AppModule::class,
                AudioModule::class,
                DatabaseModule::class
        )
)
interface AppComponent {
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
    fun inject(backendView: PlayerService)

    fun plusFragments(): FragmentComponent
}

