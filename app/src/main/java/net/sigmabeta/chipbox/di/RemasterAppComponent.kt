package net.sigmabeta.chipbox.di

import dagger.Component
import dagger.android.AndroidInjectionModule
import javax.inject.Singleton

@Singleton
@Component(
        modules = [
            RemasterAppModule::class,
            AndroidInjectionModule::class,

        ]
)
interface RemasterAppComponent {
}