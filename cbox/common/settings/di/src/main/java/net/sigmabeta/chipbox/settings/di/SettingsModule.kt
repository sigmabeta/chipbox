package net.sigmabeta.chipbox.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.real.RealChipboxSettingsManager
import net.sigmabeta.sage.storage.common.Storage

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideChipboxSettingsManager(storage: Storage): ChipboxSettingsManager =
        RealChipboxSettingsManager(storage)
}
