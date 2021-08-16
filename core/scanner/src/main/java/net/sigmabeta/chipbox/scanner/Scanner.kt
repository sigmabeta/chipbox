package net.sigmabeta.chipbox.scanner

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onStart
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import timber.log.Timber

abstract class Scanner(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    abstract suspend fun CoroutineScope.scan()

    fun startScan() {
        scannerScope.launch {
            try {
                scan()
            } catch (ex: Exception) {
                Timber.e("Scan error.")
                ex.printStackTrace()
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
}
