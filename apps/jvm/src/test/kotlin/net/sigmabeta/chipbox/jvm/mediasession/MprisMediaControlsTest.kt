package net.sigmabeta.chipbox.jvm.mediasession

import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.sage.logging.Hatchet
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the inbound transport methods — the bus-call -> [net.sigmabeta.chipbox.player.director.Director]
 * forwarding and the `Properties` surface. No bus connection is opened, so the outbound `signal`
 * calls are no-ops; the [FakeDirector] records what each method dispatched.
 */
class MprisMediaControlsTest {

    private val director = FakeDirector()
    private val controls = MprisMediaControls(director, NoopHatchet)

    @Test
    fun `Play Pause Stop Next Previous forward to the director`() {
        controls.Play()
        controls.Pause()
        controls.Stop()
        controls.Next()
        controls.Previous()

        assertEquals(1, director.playCalls)
        assertEquals(1, director.pauseCalls)
        assertEquals(1, director.stopCalls)
        assertEquals(1, director.skipForwardCalls)
        assertEquals(1, director.skipBackCalls)
    }

    @Test
    fun `PlayPause resumes when not already playing`() {
        controls.PlayPause()

        assertEquals(1, director.playCalls)
        assertEquals(0, director.pauseCalls)
    }

    @Test
    fun `Seek converts a microsecond offset to a millisecond position`() {
        controls.Seek(2_000_000L)

        assertEquals(listOf(2_000L), director.seekCalls)
    }

    @Test
    fun `SetPosition seeks to the absolute millisecond position`() {
        controls.SetPosition(DBusPath("/net/sigmabeta/chipbox/track/1"), 3_000_000L)

        assertEquals(listOf(3_000L), director.seekCalls)
    }

    @Test
    fun `Set Volume forwards the scale to the director`() {
        controls.Set(PLAYER_IFACE, "Volume", 0.5)

        assertEquals(listOf(0.5), director.setVolumeCalls)
    }

    @Test
    fun `Set Shuffle forwards to the director`() {
        controls.Set(PLAYER_IFACE, "Shuffle", true)

        assertEquals(listOf(true), director.setShuffledCalls)
    }

    @Test
    fun `Set LoopStatus maps to a repeat mode`() {
        controls.Set(PLAYER_IFACE, "LoopStatus", "Track")

        assertEquals(listOf(RepeatMode.ONE), director.setRepeatModeCalls)
    }

    @Test
    fun `Get returns the variant for a known property`() {
        val status: Variant<*> = controls.Get(PLAYER_IFACE, "PlaybackStatus")

        assertEquals("Stopped", status.value)
    }

    @Test
    fun `GetAll exposes the player interface properties`() {
        assertTrue(controls.GetAll(PLAYER_IFACE).containsKey("CanControl"))
    }

    private object NoopHatchet : Hatchet {
        override fun v(message: String) = Unit
        override fun d(message: String) = Unit
        override fun i(message: String) = Unit
        override fun w(message: String) = Unit
        override fun e(message: String) = Unit
        override fun log(severity: Int, message: String) = Unit
    }
}
