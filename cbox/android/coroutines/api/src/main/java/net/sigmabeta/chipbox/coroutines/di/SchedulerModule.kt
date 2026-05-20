package net.sigmabeta.chipbox.coroutines.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import net.sigmabeta.chipbox.coroutines.ChipboxScheduler
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.list.SageScheduler

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object SchedulerModule {
    @Provides
    @Singleton
    fun provideScheduler(impl: ChipboxScheduler): SageScheduler = impl
}
