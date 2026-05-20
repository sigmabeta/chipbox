package net.sigmabeta.chipbox.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.storage.ChipboxDataStore
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object StorageModule {
    private val Context.settingsDataStore by preferencesDataStore(name = "chipbox_settings")

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideStorage(
        dataStore: DataStore<Preferences>,
        coroutineScope: CoroutineScope,
        dispatchers: SageDispatchers,
        hatchet: Hatchet,
    ): Storage = ChipboxDataStore(
        dataStore = dataStore,
        coroutineScope = coroutineScope,
        dispatchers = dispatchers,
        hatchet = hatchet,
    )
}
