package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.sage.storage.common.Storage
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM analog of Android's `ChipboxDataStore` — an in-memory [Storage] backed by a
 * [ConcurrentHashMap] of [MutableStateFlow]s. Values do not survive process death; persistence
 * lands when a real settings file (java.util.Properties or kotlinx.serialization JSON) is
 * worth the work. For the desktop bootstrap the in-memory shape is enough: settings managers
 * read defaults, the user adjusts in-session, the next run starts fresh.
 *
 * Thread-safe: backing map is concurrent, per-key flows are MutableStateFlow.
 */
class JvmStorage : Storage {
    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val intFlows = ConcurrentHashMap<String, MutableStateFlow<Int?>>()

    override fun saveString(key: String, value: String) {
        stringFlow(key).value = value
    }

    override fun savedStringFlow(key: String): Flow<String?> = stringFlow(key).asStateFlow()

    override fun saveInt(key: String, value: Int) {
        intFlow(key).value = value
    }

    override fun savedIntFlow(key: String): Flow<Int?> = intFlow(key).asStateFlow()

    private fun stringFlow(key: String): MutableStateFlow<String?> =
        stringFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun intFlow(key: String): MutableStateFlow<Int?> =
        intFlows.getOrPut(key) { MutableStateFlow(null) }
}
