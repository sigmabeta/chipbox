package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Why a browser-state store reported an event. */
enum class BrowserStateReason {
    /** First run / cleared storage. Normal; never shown to the user. */
    MISSING,
    CORRUPT,
    UNSUPPORTED_VERSION,

    /** An older envelope was read successfully; it is re-encoded at the current version on the next write. */
    MIGRATED,
    READ_FAILED,
    WRITE_FAILED,
}

/**
 * One non-fatal store event. Decode problems name the [key] so the web host can show a specific
 * message ("Saved playlists were unreadable…") instead of a generic failure.
 */
data class BrowserStateEvent(
    val key: String,
    val reason: BrowserStateReason,
    val detail: String? = null,
) {
    /** Whether this event is worth interrupting the user with. A missing key is not. */
    val userVisible: Boolean
        get() = reason != BrowserStateReason.MISSING

    val message: String
        get() {
            val noun = label
            return when (reason) {
                BrowserStateReason.MISSING -> "No saved $noun yet."

                BrowserStateReason.CORRUPT -> "Saved $noun were unreadable; $fallback."

                BrowserStateReason.UNSUPPORTED_VERSION ->
                    "Saved $noun use a newer version of Chipbox; $fallback."

                BrowserStateReason.MIGRATED -> "Upgraded saved $noun from an older format."

                BrowserStateReason.READ_FAILED -> "Couldn't read saved $noun; browser storage may be unavailable."

                BrowserStateReason.WRITE_FAILED -> "Couldn't save $noun; browser storage is full or unavailable."
            }
        }

    /** Log form for the browser console / Error Log. */
    val logMessage: String
        get() = buildString {
            append(message)
            append(" (")
            append(key)
            if (!detail.isNullOrBlank()) {
                append(": ")
                append(detail)
            }
            append(')')
        }

    private val label: String
        get() = when (key) {
            BrowserStateKeys.FAVORITES -> "favorites"
            BrowserStateKeys.PLAYLISTS -> "playlists"
            BrowserStateKeys.PLAYBACK_HISTORY -> "play history"
            BrowserStateKeys.PLAYBACK_SESSION -> "playback session"
            else -> key
        }

    /** What the store falls back to after ignoring an unreadable value. */
    private val fallback: String
        get() = when (key) {
            BrowserStateKeys.PLAYBACK_HISTORY -> "starting with an empty history"
            BrowserStateKeys.PLAYBACK_SESSION -> "starting without a saved session"
            else -> "starting with an empty list"
        }
}

/**
 * Non-fatal event sink for the browser-local stores.
 *
 * Repositories never throw for corrupt/quota/read failures: they report here and fall back to
 * their empty value. [userMessages] is the small seam the web host collects to show a snackbar;
 * [log] lets the DI layer mirror the same event to the app logger (debug console / Error Log).
 */
class BrowserStateEvents(
    private val log: (BrowserStateEvent) -> Unit = {},
) {
    private val _events = MutableSharedFlow<BrowserStateEvent>(
        // Replay one so a startup decode failure is not lost when a screen's collector wins the
        // race against ChipboxAppUi's snackbar collector. There is exactly one user-facing
        // collector, so replay cannot duplicate a snackbar.
        replay = 1,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every event, including the informational [BrowserStateReason.MISSING]. */
    val events: SharedFlow<BrowserStateEvent> = _events.asSharedFlow()

    /** User-facing messages only (no first-run "nothing saved yet" noise). */
    val userMessages: Flow<String> = events.filter { it.userVisible }.map { it.message }

    private val reportedReadProblems = mutableSetOf<String>()

    fun report(event: BrowserStateEvent) {
        // Several screens collect the same favorites/playlists flow. A corrupt blob would otherwise
        // report once per collector (and once per screen). Read-side problems are deduped by key and
        // detail; write failures are always reported because each failed write is a distinct action.
        if (event.reason.reportedOnce && !reportedReadProblems.add(event.signature)) return
        log(event)
        _events.tryEmit(event)
    }

    private val BrowserStateReason.reportedOnce: Boolean
        get() = this != BrowserStateReason.WRITE_FAILED

    private val BrowserStateEvent.signature: String
        get() = "$key|${reason.name}|${detail.orEmpty()}"

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
