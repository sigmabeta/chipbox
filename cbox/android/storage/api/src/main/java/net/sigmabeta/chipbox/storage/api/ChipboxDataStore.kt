package net.sigmabeta.chipbox.storage.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage

class ChipboxDataStore(
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: SageDispatchers,
    private val hatchet: Hatchet,
) : Storage {
    override fun saveString(key: String, value: String) {
        coroutineScope.launch(dispatchers.disk) {
            hatchet.v("Saving string to storage: $key -> $value")
            val typedKey = stringPreferencesKey(key)
            dataStore.edit { it[typedKey] = value }
        }
    }

    override fun savedStringFlow(key: String): Flow<String?> = dataStore
        .data
        .map { it[stringPreferencesKey(key)] }

    override fun saveInt(key: String, value: Int) {
        coroutineScope.launch(dispatchers.disk) {
            hatchet.v("Saving integer to storage: $key -> $value")
            val typedKey = intPreferencesKey(key)
            dataStore.edit { it[typedKey] = value }
        }
    }

    override fun savedIntFlow(key: String): Flow<Int?> = dataStore
        .data
        .map { it[intPreferencesKey(key)] }
}
