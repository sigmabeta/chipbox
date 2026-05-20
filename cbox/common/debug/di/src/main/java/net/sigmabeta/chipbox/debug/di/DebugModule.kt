package net.sigmabeta.chipbox.debug.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.storage.common.Storage

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object DebugModule {
    @Provides
    @Singleton
    fun provideDebugSettingsManager(storage: Storage): DebugSettingsManager = RealDebugSettingsManager(storage)
}
