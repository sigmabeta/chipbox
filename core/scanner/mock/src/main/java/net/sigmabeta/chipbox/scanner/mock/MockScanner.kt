package net.sigmabeta.chipbox.scanner.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.chipbox.scanner.Scanner
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class MockScanner(
    private val mockRepository: MockRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner(dispatcher) {
    @OptIn(ExperimentalTime::class)
    override suspend fun CoroutineScope.scan() {
        emitState(ScannerState.Scanning)

        var gamesFound = 0
        var tracksFound = 0
        val games = mockRepository.getLatestAllGames()

        val duration = measureTime {
            games.forEach {
                val event = ScannerEvent.GameFoundEvent(
                    it.title,
                    it.tracks?.size ?: 0,
                    it.photoUrl ?: ""
                )

                gamesFound++
                tracksFound += it.tracks?.size ?: 0

                emitEvent(event)
                delay(100)
            }
        }

        val completeState = ScannerState.Complete(
            duration.inWholeSeconds.toInt(),
            gamesFound,
            tracksFound,
            0
        )

        emitEvent(ScannerEvent.Unknown)
        emitState(completeState)
    }
}