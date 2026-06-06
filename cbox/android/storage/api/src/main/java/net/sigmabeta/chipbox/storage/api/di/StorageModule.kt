package net.sigmabeta.chipbox.storage.api.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.storage.api.AndroidDataStore
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage

@BindingContainer
@ContributesTo(AppScope::class)
object StorageModule {
    // The DataStore is consumed only by AndroidDataStore here, so inline its
    // construction inside provideStorage instead of exposing DataStore<Preferences>
    // as its own binding. Metro 1.1.1's cross-module Provider generation chokes on
    // `DataStore<Preferences>` ("Encountered an unexpected error while processing
    // type: 'dev.zacsweers.metro.Provider<out <error>>'") when the binding is
    // consumed transitively from another module's graph. Keeping DataStore off the
    // binding surface sidesteps the bug; AndroidDataStore still gets exactly one
    // instance because @SingleIn(AppScope::class) is on this @Provides.
    private val Context.settingsDataStore by preferencesDataStore(name = "chipbox_settings")

    @Provides
    @SingleIn(AppScope::class)
    fun provideStorage(
        context: Context,
        coroutineScope: CoroutineScope,
        dispatchers: SageDispatchers,
        hatchet: Hatchet,
    ): Storage = AndroidDataStore(
        dataStore = context.settingsDataStore,
        coroutineScope = coroutineScope,
        dispatchers = dispatchers,
        hatchet = hatchet,
    )
}
