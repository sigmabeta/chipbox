package net.sigmabeta.chipbox.features.rescanstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class RescanStatusViewModel @Inject constructor(
    private val scanner: Scanner,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<RescanStatusState>(
    RescanStatusState(),
    stringProvider,
    hatchet,
) {
    // The latest scan progress and the accumulated/pending change events are buffered in these
    // fields and flushed to the rendered state on a fixed cadence (see [flush]). viewModelScope is
    // Main-confined, so the state collector, the event collector, and the ticker all mutate these
    // serially — no synchronization needed.
    private var phase = ScanPhase.IDLE
    private var timeInSeconds = 0
    private var gamesFound = 0
    private var tracksFound = 0
    private var tracksFailed = 0
    private var failedPath: String? = null

    private val rendered = mutableListOf<ScanEventItem>()
    private val pending = mutableListOf<ScanEventItem>()
    private var nextEventId = 0L

    // The file currently being read, surfaced live under Progress. Driven only by the high-frequency
    // FileScanned heartbeat — never by the game-change events — and kept separate from the batched
    // [rendered]/[pending] Changes list so it can refresh on the faster tick.
    private var currentFile: String? = null

    // Snapshot of [rendered] reused between flushes so the fast tick republishes the SAME list
    // instance — equal-state suppression stays O(1) and only the changing latestEvent recomposes.
    private var eventsSnapshot: List<ScanEventItem> = emptyList()

    init {
        viewModelScope.launch {
            scanner.state().collect { state -> absorb(state) }
        }
        viewModelScope.launch {
            scanner.scanEvents().collect { event ->
                when (event) {
                    // Live per-file heartbeat for the Progress row; never enters the Changes list.
                    // FolderScanned is ignored — folders now read concurrently, so "current folder"
                    // would just flip between unrelated folders and mean nothing.
                    is ScannerEvent.FileScanned -> currentFile = event.name

                    else -> event.toItemOrNull()?.let(pending::add)
                }
            }
        }
        // The Progress per-file marquee refreshes on a fast tick for live feedback — capped so it
        // updates no more often than once per LATEST_EVENT_INTERVAL_MS. Cheap: it republishes the
        // same events-list instance, so only the marquee row recomposes.
        viewModelScope.launch {
            while (isActive) {
                delay(LATEST_EVENT_INTERVAL_MS)
                publish()
            }
        }
        // Perf mitigation: batch the full Changes list into at most one state emission per second
        // instead of recomposing on every scanner callback (a large library fires thousands).
        viewModelScope.launch {
            while (isActive) {
                delay(BATCH_INTERVAL_MS)
                flush()
            }
        }
    }

    private fun absorb(state: ScannerState) {
        when (state) {
            is ScannerState.Scanning -> {
                // Entering Scanning from any settled phase means a fresh run — drop the previous
                // run's trailing file so Progress doesn't show a stale name before the first read.
                if (phase != ScanPhase.SCANNING) {
                    currentFile = null
                }
                phase = ScanPhase.SCANNING
                timeInSeconds = state.timeInSeconds
                gamesFound = state.gamesFound
                tracksFound = state.tracksFound
                tracksFailed = state.tracksFailed
                failedPath = null
            }

            is ScannerState.Complete -> {
                phase = ScanPhase.COMPLETE
                timeInSeconds = state.timeInSeconds
                gamesFound = state.gamesFound
                tracksFound = state.tracksFound
                tracksFailed = state.tracksFailed
                failedPath = null
            }

            is ScannerState.Failed -> {
                phase = ScanPhase.FAILED
                failedPath = state.path
            }

            ScannerState.Idle, ScannerState.Unknown -> phase = ScanPhase.IDLE
        }
    }

    // Drains pending events (in arrival order) into the rendered list, refreshes the reused
    // snapshot, and republishes. The State reverses [events] for display so newest shows on top.
    private fun flush() {
        if (pending.isNotEmpty()) {
            rendered.addAll(pending)
            pending.clear()
            eventsSnapshot = rendered.toList()
        }
        publish()
    }

    // Rebuilds and republishes the rendered state from the buffered fields. Building an equal
    // RescanStatusState is a no-op for the StateFlow, so quiet ticks don't recompose; reusing
    // [eventsSnapshot] keeps that equality check O(1) on the fast tick.
    private fun publish() {
        updateState {
            RescanStatusState(
                phase = phase,
                timeInSeconds = timeInSeconds,
                gamesFound = gamesFound,
                tracksFound = tracksFound,
                tracksFailed = tracksFailed,
                failedPath = failedPath,
                currentFile = currentFile,
                events = eventsSnapshot,
            )
        }
    }

    private fun ScannerEvent.toItemOrNull(): ScanEventItem? = when (this) {
        is ScannerEvent.GameFoundEvent ->
            ScanEventItem(nextEventId++, name, ScanEventKind.ADDED, trackCount, gameId = id, imageUrl = imageUrl)

        is ScannerEvent.GameUpdated ->
            ScanEventItem(nextEventId++, name, ScanEventKind.UPDATED, trackCount, gameId = id, imageUrl = imageUrl)

        is ScannerEvent.GameRemoved ->
            ScanEventItem(nextEventId++, name, ScanEventKind.REMOVED, 0, gameId = null, imageUrl = null)

        // Not meaningful changes — handled separately as the live Progress heartbeats.
        is ScannerEvent.FolderScanned, is ScannerEvent.FileScanned, ScannerEvent.Unknown -> null
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is RescanStatusAction.GameClicked -> emit(NavigateTo(GameDetail(action.gameId)))
            else -> Unit
        }
    }

    private companion object {
        private const val BATCH_INTERVAL_MS = 1000L
        private const val LATEST_EVENT_INTERVAL_MS = 500L
    }
}
