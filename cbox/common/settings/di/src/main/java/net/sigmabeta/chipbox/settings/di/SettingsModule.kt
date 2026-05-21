package net.sigmabeta.chipbox.settings.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.real.RealChipboxSettingsManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.storage.common.Storage

@BindingContainer
@ContributesTo(AppScope::class)
object SettingsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideChipboxSettingsManager(storage: Storage): ChipboxSettingsManager = RealChipboxSettingsManager(storage)
}
