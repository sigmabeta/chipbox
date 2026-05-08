package net.sigmabeta.chipbox.scanner

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet

abstract class Scanner(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hatchet: Hatchet = BluntHatchet(),
) {
    abstract suspend fun CoroutineScope.scan()

    fun startScan() {
        scannerScope.launch {
            try {
                scan()
            } catch (ex: Exception) {
                hatchet.e("Scan error. ${ex.stackTraceToString()}")
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
