package net.sigmabeta.chipbox.scanner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.utils.ioDispatcher
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet

abstract class Scanner(
    dispatcher: CoroutineDispatcher = ioDispatcher,
    private val hatchet: Hatchet = BluntHatchet(),
) {
    abstract suspend fun CoroutineScope.scan()

    fun startScan() {
        scannerScope.launch {
            try {
                scan()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // A scan must always end in a terminal state: consumers block on
                // state().first { it is Complete || Failed }, so a failure that escapes scan()
                // without one hangs every consumer forever. Catch Throwable (not just Exception) —
                // an Error such as a native-lib LinkageError would otherwise slip past — and surface
                // it as Failed so the UI/CLI unblock instead of spinning.
                hatchet.e("Scan error. ${error.stackTraceToString()}")
                emitState(ScannerState.Failed(error.message ?: error::class.simpleName ?: "unknown error"))
            }
        }
    }

    fun clearScan() {
        scannerScope.launch {
            emitEvent(ScannerEvent.Unknown)
            emitState(ScannerState.Idle)
        }
    }

    @OptIn(FlowPreview::class)
    fun state() = stateChannel
        .asSharedFlow()
        .onStart { emit(currentState) }

    @OptIn(FlowPreview::class)
    fun scanEvents() = eventChannel
        .asSharedFlow()
        .onStart { emit(lastEvent) }

    private var currentState: ScannerState = ScannerState.Unknown
    private var lastEvent: ScannerEvent = ScannerEvent.Unknown

    private val scannerScope = CoroutineScope(dispatcher)

    private val eventChannel = MutableSharedFlow<ScannerEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val stateChannel = MutableSharedFlow<ScannerState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Live subscriber counts on the state / event streams, for a test fake to re-publish. A UI-test
     * harness can wait on these until a reactive consumer (e.g. the Home scan-status card) is
     * actually collecting before it drives events: the streams are `replay = 1` / `DROP_OLDEST` and
     * `onStart`-replay only the *last* event, so anything emitted before the collector subscribes is
     * unrecoverable (a folder heartbeat emitted just before a file one is gone for good).
     */
    protected val stateSubscriptionCount: StateFlow<Int> get() = stateChannel.subscriptionCount
    protected val eventSubscriptionCount: StateFlow<Int> get() = eventChannel.subscriptionCount

    protected suspend fun emitState(state: ScannerState) {
        currentState = state
        stateChannel.emit(state)
    }

    protected suspend fun emitEvent(event: ScannerEvent) {
        lastEvent = event
        eventChannel.emit(event)
    }

    protected fun isFailedAlready() = currentState is ScannerState.Failed
}
