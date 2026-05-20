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
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
abstract class AndroidFileContentSourceModule {

    // @Singleton intentionally not present on this @Binds — Metro rejects scopes on @Binds
    // declarations (Hilt was tolerant). The provideAndroidFileContentSource() @Provides below
    // is @Singleton, so the underlying instance is still scoped; this @Binds just aliases it.
    @Binds
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
