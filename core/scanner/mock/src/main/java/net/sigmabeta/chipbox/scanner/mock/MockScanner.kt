package net.sigmabeta.chipbox.scanner.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onStart
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.chipbox.scanner.Scanner
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class MockScanner(
    private val mockRepository: MockRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner {
    private var currentState: ScannerState = ScannerState.Unknown
    private var lastEvent: ScannerEvent = ScannerEvent.Unknown

    private val coroutineScope = CoroutineScope(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventChannel = BroadcastChannel<ScannerEvent>(BUFFERED)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stateChannel = BroadcastChannel<ScannerState>(BUFFERED)

    @OptIn(FlowPreview::class)
    override fun state() = stateChannel
        .asFlow()
        .onStart{ emit(currentState) }

    @OptIn(FlowPreview::class)
    override fun scanEvents() = eventChannel
        .asFlow()
        .onStart{ emit(lastEvent) }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun startScan() {
        coroutineScope.launch {
            currentState = ScannerState.Scanning
            stateChannel.send(ScannerState.Scanning)

            var gamesFound = 0
            var tracksFound = 0
            val games = mockRepository.getAllGames()

            val duration = measureTime {
                games.forEach {
                    val event = ScannerEvent.GameFoundEvent(
                        it.title,
                        it.tracks?.size ?: 0,
                        it.photoUrl ?: ""
                    )

                    gamesFound++
                    tracksFound += it.tracks?.size ?: 0

                    lastEvent = event
                    eventChannel.send(event)
                    delay(100)
                }
            }

            val completeEvent = ScannerState.Complete(
                duration.inWholeSeconds.toInt(),
                gamesFound,
                tracksFound,
                0
            )

            lastEvent = ScannerEvent.Unknown
            currentState = completeEvent
            stateChannel.send(completeEvent)
        }
    }
}