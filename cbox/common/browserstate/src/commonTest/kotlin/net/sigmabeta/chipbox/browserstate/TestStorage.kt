package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import net.sigmabeta.sage.storage.common.Storage

/**
 * In-memory [Storage] that mirrors the JS adapter closely enough for store tests: per-key state
 * flows, a raw map that a "different tab" can mutate, and switchable read/write failures.
 */
internal class FakeStorage(initial: Map<String, String> = emptyMap()) : Storage {
    private val values = initial.toMutableMap()
    private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int?>>()

    var failWrites = false
    var failReads = false

    /**
     * When set, every string read fails on collection with this throwable. Used to model a read
     * cancelled mid-flight (a `CancellationException`) rather than a storage failure.
     */
    var readFailure: Throwable? = null

    /**
     * When true, [savedStringFlow] snapshots the raw value at collection start and yields once
     * before emitting it, so a second read-modify-write can interleave between another caller's
     * read and write — the same-tab lost-update race.
     */
    var interleaveReads = false

    override fun saveString(key: String, value: String) {
        if (failWrites) throw IllegalStateException("quota exceeded")
        values[key] = value
        stringFlow(key).value = value
    }

    override fun savedStringFlow(key: String): Flow<String?> {
        readFailure?.let { failure -> return flow { throw failure } }
        if (failReads) throw IllegalStateException("storage unavailable")
        val live = stringFlow(key).asStateFlow()
        if (!interleaveReads) return live
        return flow {
            val snapshot = values[key]
            yield()
            emit(snapshot)
        }
    }

    override fun saveInt(key: String, value: Int) {
        if (failWrites) throw IllegalStateException("quota exceeded")
        values[key] = value.toString()
        intFlow(key).value = value
    }

    override fun savedIntFlow(key: String): Flow<Int?> = intFlow(key).asStateFlow()

    /** Simulates another tab's `storage` event: update the raw value and wake subscribers. */
    fun writeFromOtherTab(key: String, value: String) {
        values[key] = value
        stringFlow(key).value = value
    }

    fun raw(key: String): String? = values[key]

    private fun stringFlow(key: String): MutableStateFlow<String?> =
        stringFlows.getOrPut(key) { MutableStateFlow(values[key]) }

    private fun intFlow(key: String): MutableStateFlow<Int?> =
        intFlows.getOrPut(key) { MutableStateFlow(values[key]?.toIntOrNull()) }
}

internal class FakeClock(private var next: Long = 1L) {
    fun now(): Long = next++
}
