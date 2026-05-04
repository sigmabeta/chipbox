package net.sigmabeta.chipbox.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.list.SageScheduler
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.chipbox.feature.welcome.WelcomeViewModelBrain
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

@Module
@InstallIn(SingletonComponent::class)
object ChipboxModule {
    @Provides
    @Singleton
    fun provideStringProvider(@ApplicationContext context: Context): StringProvider =
        AndroidStringProvider(context.resources) { 0 }

    @Provides
    @Singleton
    fun provideHatchet(): Hatchet = AndroidHatchet()

    @Provides
    @Singleton
    fun provideScheduler(impl: SchedulerImpl): SageScheduler = impl

    @Provides
    fun provideWelcomeBrain(
        stringProvider: StringProvider,
        analytics: Analytics,
        hatchet: Hatchet,
        scheduler: SageScheduler,
    ): WelcomeViewModelBrain = WelcomeViewModelBrain(
        stringProvider,
        analytics,
        hatchet,
        scheduler,
    )
}
