package net.sigmabeta.chipbox.scanner.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.chipbox.scanner.Scanner
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.measureTime

private const val GAME_FOUND_DELAY_MS = 100L

class MockScanner(
    private val mockRepository: MockRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner(dispatcher) {
    @OptIn(ExperimentalTime::class)
    override suspend fun CoroutineScope.scan() {
        val scanStart = TimeSource.Monotonic.markNow()
        emitState(ScannerState.Scanning())

        var gamesFound = 0
        var tracksFound = 0
        val games = mockRepository.getLatestAllGames(false, false)

        val duration = measureTime {
            games.forEach {
                val event = ScannerEvent.GameFoundEvent(
                    it.id,
                    it.title,
                    it.tracks?.size ?: 0,
                    it.photoUrl ?: ""
                )

                gamesFound++
                tracksFound += it.tracks?.size ?: 0

                emitEvent(event)
                emitState(
                    ScannerState.Scanning(
                        scanStart.elapsedNow().inWholeSeconds.toInt(),
                        gamesFound,
                        tracksFound,
                        0,
                    )
                )
                delay(GAME_FOUND_DELAY_MS)
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
