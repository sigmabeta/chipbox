package net.sigmabeta.chipbox.js.storage

import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.sage.storage.common.Storage
import org.w3c.dom.StorageEvent
import org.w3c.dom.events.Event

/**
 * Browser-backed [Storage] — keeps values in `window.localStorage` so settings survive page
 * reloads. The `Storage` contract returns a [Flow] that emits the current value AND updates
 * when the value changes; localStorage itself doesn't push, so we keep a per-key
 * [MutableStateFlow] cache that mirrors localStorage. Writes go to both: localStorage for
 * persistence, the in-process flow to wake up any subscriber.
 *
 * Cross-tab sync: the `storage` window event fires in OTHER tabs of the same origin (not the
 * tab that did the write). Listening for it lets a settings change in tab A push the new value
 * into tab B's MutableStateFlow, so a flow that's been collected in B updates without a reload.
 * Same-tab writes go through `saveString` / `saveInt` directly and don't fire the event.
 *
 * Single instance per app (singleton DI binding) so every consumer of a given key shares the
 * same flow.
 */
class LocalStorageStorage : Storage {

    private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int?>>()

    init {
        window.addEventListener("storage", { event ->
            val storageEvent = event.unsafeCast<StorageEvent>()
            // Only react to changes within OUR localStorage (StorageEvent also fires for
            // sessionStorage). `event.storageArea` is null in some edge cases (clear()
            // bubbling); treat null storageArea as "localStorage" since sessionStorage isn't
            // ever touched by this class.
            val area = storageEvent.storageArea
            if (area != null && area != window.localStorage) return@addEventListener
            val key = storageEvent.key
            if (key == null) {
                // A null key means the *whole* storage was cleared (e.g. via DevTools). Reset
                // every cached flow to whatever localStorage now reports — usually null — so
                // subscribers see the wipe.
                stringFlows.forEach { (k, flow) -> flow.value = window.localStorage.getItem(k) }
                intFlows.forEach { (k, flow) -> flow.value = window.localStorage.getItem(k)?.toIntOrNull() }
                return@addEventListener
            }
            val newValue = storageEvent.newValue
            stringFlows[key]?.value = newValue
            intFlows[key]?.value = newValue?.toIntOrNull()
        })
    }

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
