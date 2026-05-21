package net.sigmabeta.chipbox.coroutines.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.coroutines.ChipboxScheduler
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.list.SageScheduler

@BindingContainer
@ContributesTo(AppScope::class)
object SchedulerModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduler(impl: ChipboxScheduler): SageScheduler = impl
}
