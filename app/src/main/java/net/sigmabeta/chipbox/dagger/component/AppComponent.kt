package net.sigmabeta.chipbox.dagger.component

import dagger.Component
import net.sigmabeta.chipbox.backend.PlayerService
import net.sigmabeta.chipbox.backend.ScanService
import net.sigmabeta.chipbox.backend.module.AudioModule
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.dagger.module.PreferenceModule
import net.sigmabeta.chipbox.model.database.module.RepositoryModule
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.ui.main.MainActivity
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.onboarding.OnboardingActivity
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.ui.settings.SettingsActivity
import javax.inject.Singleton

@Singleton
@Component(
        modules = arrayOf(
                AppModule::class,
                AudioModule::class,
                RepositoryModule::class,
                PreferenceModule::class
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
    fun inject(view: GameActivity)
    fun inject(view: SettingsActivity)
    fun inject(view: OnboardingActivity)

    fun inject(backendView: PlayerService)
    fun inject(backendView: ScanService)

    fun plusFragments(): FragmentComponent
}

