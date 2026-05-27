package net.sigmabeta.chipbox.features.rescanstatus

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RescanStatusViewModel] reduces two scanner flows (state + events) into one batched UI state,
 * flushed on a 1-second tick. The batching is a perf mitigation — a fast scan can emit thousands
 * of events, and a per-event state recomposition would tank the screen.
 *
 * The tests below share a single [TestCoroutineScheduler] between `Dispatchers.Main` and the
 * runTest body so `advanceTimeBy(BATCH_INTERVAL_MS)` actually advances the VM's flush ticker
 * (it lives on viewModelScope, which is Main-confined).
 *
 * Cleanup contract — every test wraps its body in [rescanTest] because the VM's flush ticker is
 * an infinite `while (isActive) { delay(1s); flush() }` loop. `runTest` waits up to 60s for the
 * test scheduler to idle after the body returns; without cancelling [viewModelScope] in a
 * finally, every test stalls for that full 60s grace period (deceptive — looks like a hang).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RescanStatusViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows IDLE phase with zero progress and no events`() = rescanTest { vm, _ ->
        val state = vm.state.first()
        assertEquals(ScanPhase.IDLE, state.phase)
        assertEquals(0, state.timeInSeconds)
        assertEquals(0, state.gamesFound)
        assertEquals(0, state.tracksFound)
        assertEquals(0, state.tracksFailed)
        assertNull(state.failedPath)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun `Scanner Scanning state lifts phase to SCANNING and copies the counters`() = rescanTest { vm, scanner ->
        scanner.pushState(ScannerState.Scanning(timeInSeconds = 12, gamesFound = 3, tracksFound = 25, tracksFailed = 1))
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.phase == ScanPhase.SCANNING }
        assertEquals(12, state.timeInSeconds)
        assertEquals(3, state.gamesFound)
        assertEquals(25, state.tracksFound)
        assertEquals(1, state.tracksFailed)
        assertNull(state.failedPath, "Scanning never carries a failedPath, even after a previous Failed")
    }

    @Test
    fun `Scanner Complete state moves the phase forward and preserves the final counters`() = rescanTest { vm, scanner ->
        scanner.pushState(ScannerState.Complete(timeInSeconds = 60, gamesFound = 10, tracksFound = 200, tracksFailed = 4))
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.phase == ScanPhase.COMPLETE }
        assertEquals(60, state.timeInSeconds)
        assertEquals(10, state.gamesFound)
        assertEquals(200, state.tracksFound)
        assertEquals(4, state.tracksFailed)
    }

    @Test
    fun `Scanner Failed sets phase FAILED and surfaces the failed path`() = rescanTest { vm, scanner ->
        scanner.pushState(ScannerState.Failed(path = "/sdcard/Music/bad-folder"))
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.phase == ScanPhase.FAILED }
        assertEquals("/sdcard/Music/bad-folder", state.failedPath)
    }

    @Test
    fun `Scanner Idle and Unknown both collapse the phase to IDLE`() = rescanTest { vm, scanner ->
        // Idle is the post-completion "scanner has nothing to report" state; Unknown is the
        // pre-init seed. Both should render the same empty-state on screen.
        scanner.pushState(ScannerState.Scanning(timeInSeconds = 1))
        advanceTimeBy(BATCH_FLUSH_MS)
        scanner.pushState(ScannerState.Idle)
        advanceTimeBy(BATCH_FLUSH_MS)
        assertEquals(ScanPhase.IDLE, vm.state.first { it.phase == ScanPhase.IDLE }.phase)

        scanner.pushState(ScannerState.Unknown)
        advanceTimeBy(BATCH_FLUSH_MS)
        assertEquals(ScanPhase.IDLE, vm.state.value.phase)
    }

    @Test
    fun `GameFoundEvent appears as an ADDED ScanEventItem after the batch flush`() = rescanTest { vm, scanner ->
        scanner.pushEvent(ScannerEvent.GameFoundEvent(id = 1L, name = "Chrono Trigger", trackCount = 60, imageUrl = "u"))
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.events.isNotEmpty() }
        assertEquals(1, state.events.size)
        val item = state.events.single()
        assertEquals("Chrono Trigger", item.gameName)
        assertEquals(ScanEventKind.ADDED, item.kind)
        assertEquals(60, item.trackCount)
        assertEquals(1L, item.gameId)
        assertEquals("u", item.imageUrl)
    }

    @Test
    fun `GameUpdated and GameRemoved map to their own ScanEventKinds, Removed clears gameId`() = rescanTest { vm, scanner ->
        // GameRemoved deliberately drops gameId — the row in the repo is gone, so a tap can't
        // navigate to a detail screen. The reducer encodes that by writing null.
        scanner.pushEvent(ScannerEvent.GameUpdated(id = 2L, name = "Updated", trackCount = 10, imageUrl = null))
        scanner.pushEvent(ScannerEvent.GameRemoved(name = "Removed"))
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.events.size >= 2 }
        val updated = state.events[0]
        val removed = state.events[1]
        assertEquals(ScanEventKind.UPDATED, updated.kind)
        assertEquals(2L, updated.gameId)
        assertEquals(ScanEventKind.REMOVED, removed.kind)
        assertNull(removed.gameId, "Removed events lose their gameId — the row is gone")
        assertEquals(0, removed.trackCount)
    }

    @Test
    fun `ScannerEvent Unknown does not add a row to the events list`() = rescanTest { vm, scanner ->
        // The replay=1 baseline ScannerEvent.Unknown shows up the moment the VM subscribes; the
        // reducer must filter it or every screen would start with a phantom row.
        advanceTimeBy(BATCH_FLUSH_MS)
        assertTrue(vm.state.value.events.isEmpty())

        scanner.pushEvent(ScannerEvent.Unknown)
        advanceTimeBy(BATCH_FLUSH_MS)
        assertTrue(vm.state.value.events.isEmpty(), "Unknown was filtered, not appended")
    }

    @Test
    fun `Multiple events buffered across a single tick all show up in one flushed batch`() = rescanTest { vm, scanner ->
        // The performance contract — N events fired within one tick produce ONE state update
        // carrying all N items. This is the whole reason for the BATCH_INTERVAL_MS gate.
        repeat(5) { idx ->
            scanner.pushEvent(
                ScannerEvent.GameFoundEvent(id = idx.toLong(), name = "G$idx", trackCount = 1, imageUrl = null),
            )
        }
        advanceTimeBy(BATCH_FLUSH_MS)

        val state = vm.state.first { it.events.size == 5 }
        assertEquals(listOf("G0", "G1", "G2", "G3", "G4"), state.events.map { it.gameName })
    }

    @Test
    fun `event ids increment monotonically so a re-flush keeps row keys stable`() = rescanTest { vm, scanner ->
        scanner.pushEvent(ScannerEvent.GameFoundEvent(id = 1L, name = "A", trackCount = 1, imageUrl = null))
        advanceTimeBy(BATCH_FLUSH_MS)
        scanner.pushEvent(ScannerEvent.GameFoundEvent(id = 2L, name = "B", trackCount = 1, imageUrl = null))
        advanceTimeBy(BATCH_FLUSH_MS)

        val ids = vm.state.first { it.events.size == 2 }.events.map { it.id }
        assertEquals(2, ids.distinct().size, "Each scan event gets its own row id")
        assertTrue(ids[1] > ids[0], "Ids are monotonic in flush order")
    }

    @Test
    fun `GameClicked emits NavigateTo GameDetail with the supplied gameId`() = rescanTest { vm, _ ->
        val event = collectAndDispatch(vm, RescanStatusAction.GameClicked(gameId = 42L))
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(GameDetail(42L), event.destination)
    }

    // ---- helpers ----

    private fun rescanTest(
        block: suspend TestScope.(RescanStatusViewModel, CountingScanner) -> Unit,
    ) = runTest(dispatcher) {
        val scanner = CountingScanner()
        val vm = RescanStatusViewModel(
            scanner = scanner,
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
        )
        try {
            block(vm, scanner)
        } finally {
            vm.viewModelScope.coroutineContext[Job]?.cancel()
        }
    }

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: RescanStatusViewModel,
        action: RescanStatusAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        /** Mirrors the VM's private BATCH_INTERVAL_MS. One full tick triggers exactly one flush. */
        const val BATCH_FLUSH_MS = 1_000L
    }
}
