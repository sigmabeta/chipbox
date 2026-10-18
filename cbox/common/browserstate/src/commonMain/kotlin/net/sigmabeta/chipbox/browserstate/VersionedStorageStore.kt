package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import net.sigmabeta.sage.storage.common.Storage

/**
 * A single JSON value persisted under [key] with a [VersionedEnvelope].
 *
 * Reads are derived from [Storage.savedStringFlow] (never an init-only copy), so another tab's
 * `storage` event propagates without any extra subscription. Writes go through
 * [Storage.saveString] so the JS adapter writes `window.localStorage` and wakes its per-key flow.
 *
 * Every failure is non-fatal: decode problems report a [BrowserStateEvent] and fall back to
 * [emptyValue]; a read or quota (`DOMException`) failure reports and returns the previous/empty
 * value instead of throwing through the UI.
 */
class VersionedStorageStore<T>(
    private val storage: Storage,
    val key: String,
    private val codec: VersionedJsonCodec<T>,
    private val emptyValue: T,
    private val events: BrowserStateEvents,
) {
    fun values(): Flow<T> = flow {
        val source = try {
            storage.savedStringFlow(key)
        } catch (t: Throwable) {
            reportReadFailure(t)
            emit(emptyValue)
            return@flow
        }
        source.collect { raw -> emit(decode(raw)) }
    }.catch { t ->
        reportReadFailure(t)
        emit(emptyValue)
    }

    /** The current stored value. Used as the read half of a read-modify-write mutation. */
    suspend fun current(): T {
        val raw = try {
            storage.savedStringFlow(key).first()
        } catch (t: Throwable) {
            reportReadFailure(t)
            return emptyValue
        }
        return decode(raw)
    }

    /** Encode and persist [value]; returns false when storage refused the write. Never throws. */
    fun write(value: T): Boolean = try {
        storage.saveString(key, codec.encode(value))
        true
    } catch (t: Throwable) {
        events.report(BrowserStateEvent(key, BrowserStateReason.WRITE_FAILED, t.message))
        false
    }

    /**
     * Atomic-enough read-modify-write for a single-tab store. Last writer wins if two tabs race;
     * that is the documented v1 trade-off, not a merge.
     */
    suspend fun update(transform: (T) -> T): T? {
        val current = current()
        val next = transform(current)
        if (next == current) return current
        return if (write(next)) next else null
    }

    private fun decode(raw: String?): T = when (val outcome = codec.decode(raw)) {
        DecodeOutcome.Missing -> {
            events.report(BrowserStateEvent(key, BrowserStateReason.MISSING))
            emptyValue
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
            outcome.value
        }

        is DecodeOutcome.Failed -> {
            events.report(BrowserStateEvent(key, outcome.reason.toReason(), outcome.detail))
            emptyValue
        }
    }

    private fun reportReadFailure(t: Throwable) {
        events.report(BrowserStateEvent(key, BrowserStateReason.READ_FAILED, t.message))
    }

    private fun DecodeFailure.toReason(): BrowserStateReason = when (this) {
        DecodeFailure.CORRUPT -> BrowserStateReason.CORRUPT
        DecodeFailure.UNSUPPORTED_VERSION -> BrowserStateReason.UNSUPPORTED_VERSION
    }
}
