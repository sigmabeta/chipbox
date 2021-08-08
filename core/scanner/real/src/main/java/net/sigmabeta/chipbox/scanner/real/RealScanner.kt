package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onStart
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import kotlin.time.ExperimentalTime

class RealScanner(
    private val realRepository: Repository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner {
    private var currentState: ScannerState = ScannerState.Unknown
    private var lastEvent: ScannerEvent = ScannerEvent.Unknown

    private val scannerScope = CoroutineScope(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventChannel = BroadcastChannel<ScannerEvent>(BUFFERED)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stateChannel = BroadcastChannel<ScannerState>(BUFFERED)

    @OptIn(FlowPreview::class)
    override fun state() = stateChannel
        .asFlow()
        .onStart { emit(currentState) }

    @OptIn(FlowPreview::class)
    override fun scanEvents() = eventChannel
        .asFlow()
        .onStart { emit(lastEvent) }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun startScan() {

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun clearScan() {

    }
}