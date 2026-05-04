package net.sigmabeta.chipbox.contentsource

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AndroidFileContentSourceModule {
    @Provides
    @Singleton
    fun provideAndroidFileContentSource(
        @ApplicationContext context: Context,
        dispatchers: SageDispatchers,
        hatchet: Hatchet,
    ): AndroidFileContentSource = AndroidFileContentSource(context, dispatchers, hatchet)
}