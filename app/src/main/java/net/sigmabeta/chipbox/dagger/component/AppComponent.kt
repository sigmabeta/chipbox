package net.sigmabeta.chipbox.dagger.component

import dagger.Component
import net.sigmabeta.chipbox.backend.PlayerService
import net.sigmabeta.chipbox.backend.module.AudioModule
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.database.module.DatabaseModule
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
    fun inject(backendView: PlayerService)

    fun plusActivities(): ActivityComponent

    fun plusFragments(): FragmentComponent
}

