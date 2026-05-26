package net.sigmabeta.chipbox.features.playbackstatus.real

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.debuginfo.PlaybackDebugInfo
import net.sigmabeta.chipbox.debuginfo.fake.FakeDebugInfoManager
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PlaybackStatusViewModel] subscribes to a [DebugInfoManager]'s `StateFlow<PlaybackDebugInfo>`
 * in its `init` and folds each emission into `state.debug`. Plus a single user action
 * ([PlaybackStatusAction.CopyDebugInfoClicked]) that emits a clipboard event.
 *
 * The collect happens on `viewModelScope`, so the `Dispatchers.setMain(UnconfinedTestDispatcher)`
 * rig is what makes the in-test emissions land synchronously.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStatusViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state debug picks up the manager's seed snapshot immediately`() = runTest {
        // The VM's init collector subscribes to a StateFlow, which by construction always has a
        // seed value — production's RealDebugInfoManager seeds with PlaybackDebugInfo() too.
        // With UnconfinedTestDispatcher, the collector's first delivery races the constructor
        // and lands before state.first() returns. Result: state.debug is *always* non-null in
        // practice (the LCE-style "no data yet" sentinel is the empty PlaybackDebugInfo).
        val manager = FakeDebugInfoManager()
        val vm = PlaybackStatusViewModel(manager, stubStringProvider(), BluntHatchet())
        val initial = vm.state.first().debug
        assertEquals(PlaybackDebugInfo(), initial)
    }

    @Test
    fun `state debug updates each time the manager emits a fresh snapshot`() = runTest {
        // The init collector picks up every emission; this is the contract the debug screen
        // depends on for live diagnostics.
        val manager = FakeDebugInfoManager()
        val vm = PlaybackStatusViewModel(manager, stubStringProvider(), BluntHatchet())

        val snapshot1 = PlaybackDebugInfo(playback = playbackState(PlayerState.PLAYING))
        manager.emit(snapshot1)
        assertEquals(snapshot1, vm.state.first { it.debug?.playback?.state == PlayerState.PLAYING }.debug)

        val snapshot2 = PlaybackDebugInfo(playback = playbackState(PlayerState.PAUSED))
        manager.emit(snapshot2)
        assertEquals(snapshot2, vm.state.first { it.debug?.playback?.state == PlayerState.PAUSED }.debug)
    }

    @Test
    fun `CopyDebugInfoClicked emits a CopyToClipboard event`() = runTest {
        val manager = FakeDebugInfoManager()
        val vm = PlaybackStatusViewModel(manager, stubStringProvider(), BluntHatchet())

        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            vm.events.first { it is ChipboxEvent.CopyToClipboard } as ChipboxEvent.CopyToClipboard
        }
        vm.sendAction(PlaybackStatusAction.CopyDebugInfoClicked)
        val event = collector.await()

        assertEquals("Chipbox debug info", event.label, "clipboard label is the constant from the VM")
        assertTrue(event.text.isNotBlank(), "text should always be non-blank — falls back to a placeholder")
    }

    @Test
    fun `CopyDebugInfoClicked copies the seed snapshot toString when nothing has changed yet`() = runTest {
        // The VM's `?: "No debug info collected yet."` placeholder fires only when
        // `state.value.debug` is null, but production's StateFlow-based DebugInfoManager always
        // seeds a (possibly empty) PlaybackDebugInfo — so the placeholder branch is dead in
        // practice. Verify the seed snapshot's toString lands instead, which is what users
        // actually see when they tap "Copy debug info" on a fresh debug screen.
        val manager = FakeDebugInfoManager()
        val vm = PlaybackStatusViewModel(manager, stubStringProvider(), BluntHatchet())

        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            vm.events.first { it is ChipboxEvent.CopyToClipboard } as ChipboxEvent.CopyToClipboard
        }
        vm.sendAction(PlaybackStatusAction.CopyDebugInfoClicked)

        val event = collector.await()
        assertEquals(PlaybackDebugInfo().toString(), event.text)
    }

    @Test
    fun `CopyDebugInfoClicked after a snapshot copies its toString()`() = runTest {
        // The VM dumps `state.value.debug?.toString()` — verify it's the actual data-class
        // toString of the captured snapshot, not some derived string.
        val manager = FakeDebugInfoManager()
        val vm = PlaybackStatusViewModel(manager, stubStringProvider(), BluntHatchet())

        val snapshot = PlaybackDebugInfo(playback = playbackState(PlayerState.PLAYING))
        manager.emit(snapshot)
        vm.state.first { it.debug != null }

        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            vm.events.first { it is ChipboxEvent.CopyToClipboard } as ChipboxEvent.CopyToClipboard
        }
        vm.sendAction(PlaybackStatusAction.CopyDebugInfoClicked)

        val event = collector.await()
        assertEquals(snapshot.toString(), event.text)
    }

    // ---- helpers ----

    private fun playbackState(state: PlayerState): ChipboxPlaybackState = ChipboxPlaybackState(
        state = state,
        position = 0L,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
        errorMessage = null,
    )

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
