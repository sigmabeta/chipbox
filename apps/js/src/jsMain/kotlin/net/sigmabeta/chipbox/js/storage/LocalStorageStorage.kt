package net.sigmabeta.chipbox.js.storage

import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.sage.storage.common.Storage

/**
 * Browser-backed [Storage] — keeps values in `window.localStorage` so settings survive page
 * reloads. The `Storage` contract returns a [Flow] that emits the current value AND updates
 * when the value changes; localStorage itself doesn't push, so we keep a per-key
 * [MutableStateFlow] cache that mirrors localStorage. Writes go to both: localStorage for
 * persistence, the in-process flow to wake up any subscriber.
 *
 * Single instance per app (singleton DI binding) so every consumer of a given key shares the
 * same flow. Other tabs of the same origin won't see each other's writes — the `storage`
 * window event could be wired in to bridge them, but we don't currently need cross-tab sync.
 */
class LocalStorageStorage : Storage {

    private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int?>>()

    override fun saveString(key: String, value: String) {
        window.localStorage.setItem(key, value)
        stringFlow(key).value = value
    }

    override fun savedStringFlow(key: String): Flow<String?> = stringFlow(key).asStateFlow()

    override fun saveInt(key: String, value: Int) {
        window.localStorage.setItem(key, value.toString())
        intFlow(key).value = value
        // Keep string-typed subscribers (if any) consistent — chipbox uses one type per key
        // but the contract leaves overlap open.
        stringFlows[key]?.value = value.toString()
    }

    override fun savedIntFlow(key: String): Flow<Int?> = intFlow(key).asStateFlow()

    private fun stringFlow(key: String): MutableStateFlow<String?> =
        stringFlows.getOrPut(key) { MutableStateFlow(window.localStorage.getItem(key)) }

    private fun intFlow(key: String): MutableStateFlow<Int?> =
        intFlows.getOrPut(key) { MutableStateFlow(window.localStorage.getItem(key)?.toIntOrNull()) }
}
