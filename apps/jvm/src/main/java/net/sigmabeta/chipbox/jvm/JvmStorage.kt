package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.sage.storage.common.Storage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM analog of Android's `AndroidDataStore` — a file-backed [Storage]. Per-key
 * [MutableStateFlow]s give the UI the same reactive reads the Android DataStore does; a
 * `java.util.Properties` file under the work dir makes those values survive process death.
 *
 * The properties file namespaces keys by type (`string.` / `int.`) so a string key and an int
 * key that share a name stay distinct, mirroring DataStore's typed `Preferences.Key`s. Each save
 * updates the in-memory flow and rewrites the whole file (it's a handful of settings, so a full
 * rewrite is cheaper than tracking deltas); the write goes to a temp file and is atomically moved
 * into place so a crash mid-write can't truncate the live file.
 *
 * Thread-safe: backing maps are concurrent, per-key flows are MutableStateFlow, and disk writes
 * are serialized on [persistLock].
 */
class JvmStorage(private val file: File) : Storage {
    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val intFlows = ConcurrentHashMap<String, MutableStateFlow<Int?>>()
    private val persistLock = Any()

    init {
        load()
    }

    override fun saveString(key: String, value: String) {
        stringFlow(key).value = value
        persist()
    }

    override fun savedStringFlow(key: String): Flow<String?> = stringFlow(key).asStateFlow()

    override fun saveInt(key: String, value: Int) {
        intFlow(key).value = value
        persist()
    }

    override fun savedIntFlow(key: String): Flow<Int?> = intFlow(key).asStateFlow()

    private fun stringFlow(key: String): MutableStateFlow<String?> =
        stringFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun intFlow(key: String): MutableStateFlow<Int?> =
        intFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun load() {
        if (!file.exists()) return
        val props = Properties()
        file.inputStream().buffered().use(props::load)
        for (name in props.stringPropertyNames()) {
            val raw = props.getProperty(name) ?: continue
            when {
                name.startsWith(STRING_PREFIX) ->
                    stringFlow(name.removePrefix(STRING_PREFIX)).value = raw

                name.startsWith(INT_PREFIX) ->
                    raw.toIntOrNull()?.let { intFlow(name.removePrefix(INT_PREFIX)).value = it }
            }
        }
    }

    private fun persist() = synchronized(persistLock) {
        val props = Properties()
        stringFlows.forEach { (key, flow) -> flow.value?.let { props.setProperty(STRING_PREFIX + key, it) } }
        intFlows.forEach { (key, flow) -> flow.value?.let { props.setProperty(INT_PREFIX + key, it.toString()) } }

        file.parentFile?.mkdirs()
        val tmp = File.createTempFile("settings", ".tmp", file.parentFile)
        tmp.outputStream().buffered().use { props.store(it, "Chipbox desktop settings") }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val STRING_PREFIX = "string."
        const val INT_PREFIX = "int."
    }
}
