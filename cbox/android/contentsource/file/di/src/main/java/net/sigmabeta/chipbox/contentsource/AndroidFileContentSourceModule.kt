package net.sigmabeta.chipbox.contentsource

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@ContributesTo(AppScope::class)
interface AndroidFileContentSourceModule {

    // Scope intentionally not present on this @Binds — Metro rejects scopes on @Binds.
    // The provideAndroidFileContentSource() @Provides below is @SingleIn(AppScope::class),
    // so the underlying instance is still scoped; this @Binds just aliases it into the
    // multibinding Set<ContentSource>.
    @Binds
    @IntoSet
    fun bindAsContentSource(impl: AndroidFileContentSource): ContentSource

    @Multibinds
    fun contentSources(): Set<@JvmSuppressWildcards ContentSource>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideAndroidFileContentSource(
            context: Context,
            dispatchers: SageDispatchers,
            hatchet: Hatchet,
        ): AndroidFileContentSource = AndroidFileContentSource(context, dispatchers, hatchet)
    }
}
