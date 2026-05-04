package net.sigmabeta.chipbox.contentsource

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidFileContentSourceModule {

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindAsContentSource(impl: AndroidFileContentSource): ContentSource

    @Multibinds
    abstract fun contentSources(): Set<@JvmSuppressWildcards ContentSource>

    companion object {
        @Provides
        @Singleton
        fun provideAndroidFileContentSource(
            @ApplicationContext context: Context,
            dispatchers: SageDispatchers,
            hatchet: Hatchet,
        ): AndroidFileContentSource = AndroidFileContentSource(context, dispatchers, hatchet)
    }
}
