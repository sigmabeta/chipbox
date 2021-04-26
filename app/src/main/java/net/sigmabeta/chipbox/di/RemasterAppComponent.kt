package net.sigmabeta.chipbox.di

import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.activities.RemasterActivity
import javax.inject.Singleton

@Singleton
@Component(
        modules = [
            RemasterAppModule::class,
            AndroidInjectionModule::class,
            ActivityBindingModule::class
        ]
)
interface RemasterAppComponent : AndroidInjector<ChipboxApplication> {
    @Component.Factory
    abstract class Factory {
        abstract fun create(appModule: RemasterAppModule): RemasterAppComponent
    }

    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: RemasterActivity)
}
