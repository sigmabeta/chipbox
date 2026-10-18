package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sigmabeta.sage.storage.common.Storage

/**
 * A single JSON value persisted under [key] with a [VersionedEnvelope].
 *
 * Reads are derived from [Storage.savedStringFlow] (never an init-only copy), so another tab's
 * `storage` event propagates without any extra subscription. Writes go through
 * [Storage.saveString] so the JS adapter writes `window.localStorage` and wakes its per-key flow.
 *
 * Every storage failure is non-fatal: decode problems report a [BrowserStateEvent] and fall back to
 * [emptyValue]; a read or quota (`DOMException`) failure reports and returns the previous/empty
 * value instead of throwing through the UI. Cancellation is not a storage failure: a
 * `CancellationException` always propagates so a cancelled screen never emits a spurious failure
 * event.
 */
class VersionedStorageStore<T>(
    private val storage: Storage,
    val key: String,
    private val codec: VersionedJsonCodec<T>,
    private val emptyValue: T,
    private val events: BrowserStateEvents,
) {
    /**
     * Serializes read-modify-write mutations in this tab. Two coroutines updating the same store
     * can otherwise both read the old value and one write clobbers the other. Cross-tab races
     * remain last-writer-wins: two tabs have two stores and no shared lock.
     */
    private val updateMutex = Mutex()

    fun values(): Flow<T> = flow {
        val source = try {
            storage.savedStringFlow(key)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            reportReadFailure(t)
            emit(emptyValue)
            return@flow
        }
        source.collect { raw -> emit(decode(raw).value) }
    }.catch { t ->
        t.rethrowIfCancellation()
        reportReadFailure(t)
        emit(emptyValue)
    }

    /** The current stored value. Used as the read half of a read-modify-write mutation. */
    suspend fun current(): T = read().value

    /**
     * Encode and persist [value]; returns false when storage refused the write. Never throws a
     * storage failure (cancellation still propagates).
     *
     * Unlike [update], this is an unconditional write: it does not decode what is stored, so it
     * replaces a corrupt **or** newer-schema blob. [update] is the read-modify-write path and is
     * where a newer schema is protected from being rewritten; call this only for explicit user
     * intent (the session store's save/clear), where the caller has decided the write must land.
     */
    fun write(value: T): Boolean = try {
        storage.saveString(key, codec.encode(value))
        true
    } catch (t: Throwable) {
        t.rethrowIfCancellation()
        events.report(BrowserStateEvent(key, BrowserStateReason.WRITE_FAILED, t.message))
        false
    }

    /**
     * Read-modify-write for this store, serialized by [updateMutex] so concurrent mutations in one
     * tab cannot lose an update. Last writer wins if two tabs race; that is the documented v1
     * trade-off, not a merge.
     *
     * When the stored blob is corrupt it decodes as [emptyValue]; a transform that produces that
     * same empty value (an explicit clear) would otherwise look like a no-op and leave the
     * unreadable string in storage forever. So a corrupt read always forces the write.
     */
    suspend fun update(transform: (T) -> T): T? = updateMutex.withLock {
        val stored = read()
        val next = transform(stored.value)
        when {
            next == stored.value && !stored.corrupt -> stored.value
            write(next) -> next
            else -> null
        }
    }

    private suspend fun read(): Decoded<T> {
        val raw = try {
            storage.savedStringFlow(key).first()
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            reportReadFailure(t)
            return Decoded(emptyValue, corrupt = false)
        }
        return decode(raw)
    }

    private fun decode(raw: String?): Decoded<T> = when (val outcome = codec.decode(raw)) {
        DecodeOutcome.Missing -> {
            events.report(BrowserStateEvent(key, BrowserStateReason.MISSING))
            Decoded(emptyValue, corrupt = false)
        }

        is DecodeOutcome.Success -> {
            if (outcome.migratedFrom != null) {
                events.report(
                    BrowserStateEvent(
                        key,
                        BrowserStateReason.MIGRATED,
                        "schema ${outcome.migratedFrom} -> ${codec.currentVersion}",
                    ),
                )
            }
            Decoded(outcome.value, corrupt = false)
        }

        is DecodeOutcome.Failed -> {
            events.report(BrowserStateEvent(key, outcome.reason.toReason(), outcome.detail))
            // A corrupt blob can be replaced by the next write; a newer schema is never rewritten
            // by [update], or this build would destroy data a newer one still understands. An
            // explicit [write] (the session store's save/clear) is the documented exception.
            Decoded(emptyValue, corrupt = outcome.reason == DecodeFailure.CORRUPT)
        }
    }

    private fun reportReadFailure(t: Throwable) {
        events.report(BrowserStateEvent(key, BrowserStateReason.READ_FAILED, t.message))
    }

    private fun DecodeFailure.toReason(): BrowserStateReason = when (this) {
        DecodeFailure.CORRUPT -> BrowserStateReason.CORRUPT
        DecodeFailure.UNSUPPORTED_VERSION -> BrowserStateReason.UNSUPPORTED_VERSION
    }

    private class Decoded<T>(val value: T, val corrupt: Boolean)

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }
}

/**
 * One call site for the store's constructor repeated by every `Storage*Repository`: bind a stable
 * key, its codec, the empty fallback, and the shared event sink to [Storage].
 */
internal fun <T> Storage.versionedStore(
    key: String,
    codec: VersionedJsonCodec<T>,
    emptyValue: T,
    events: BrowserStateEvents,
): VersionedStorageStore<T> = VersionedStorageStore(
    storage = this,
    key = key,
    codec = codec,
    emptyValue = emptyValue,
    events = events,
)
