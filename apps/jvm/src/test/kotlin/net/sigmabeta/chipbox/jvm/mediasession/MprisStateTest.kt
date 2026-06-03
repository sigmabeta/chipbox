package net.sigmabeta.chipbox.jvm.mediasession

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pure MPRIS property model. No D-Bus connection is involved — these assert
 * the shape of the change-sets and property maps the transport would publish.
 */
class MprisStateTest {

    @Test
    fun `applyTrack maps title album artist length and trackid`() {
        val state = MprisState()

        val changed = state.applyTrack(
            trackOf(id = 7, title = "Aquatic Ambiance", gameTitle = "DKC", artistName = "David Wise", trackNumber = 3),
        )

        val meta = metadataOf(changed)
        assertEquals("Aquatic Ambiance", meta["xesam:title"]?.value)
        assertEquals("DKC", meta["xesam:album"]?.value)
        assertEquals(listOf("David Wise"), meta["xesam:artist"]?.value)
        // mpris:length is microseconds; the fixture's track is 60_000 ms.
        assertEquals(60_000L * 1_000L, meta["mpris:length"]?.value)
        assertEquals(3, meta["xesam:trackNumber"]?.value)
        assertTrue(meta["mpris:trackid"]?.value is DBusPath)
    }

    @Test
    fun `applyTrack with null clears previous metadata`() {
        val state = MprisState()
        state.applyTrack(trackOf(id = 1, title = "x"))

        val changed = state.applyTrack(null)

        assertTrue(metadataOf(changed).isEmpty())
    }

    @Test
    fun `applyPlayback maps PLAYING to Playing status`() {
        val state = MprisState()

        val change = state.applyPlayback(playbackOf(PlayerState.PLAYING))

        assertEquals("Playing", change.changed["PlaybackStatus"]?.value)
        assertEquals("Playing", state.playbackStatus)
    }

    @Test
    fun `applyPlayback omits PlaybackStatus when status is unchanged`() {
        val state = MprisState()
        state.applyPlayback(playbackOf(PlayerState.PLAYING))

        val change = state.applyPlayback(playbackOf(PlayerState.PLAYING))

        assertFalse(change.changed.containsKey("PlaybackStatus"))
    }

    @Test
    fun `applyPlayback emits Seeked only on a discontinuity`() {
        val state = MprisState()

        // First report (delta 0) and a small forward advance are ordinary playback — no Seeked.
        assertNull(state.applyPlayback(playbackOf(PlayerState.PLAYING, positionMs = 0)).seekedUs)
        assertNull(state.applyPlayback(playbackOf(PlayerState.PLAYING, positionMs = 1_000)).seekedUs)
        // A big forward jump (a user seek / skip) is a discontinuity, reported in microseconds.
        assertEquals(8_000L * 1_000L, state.applyPlayback(playbackOf(PlayerState.PLAYING, positionMs = 8_000)).seekedUs)
        // A backward jump (e.g. a track change resetting to 0) is also a discontinuity.
        assertNotNull(state.applyPlayback(playbackOf(PlayerState.PLAYING, positionMs = 0)).seekedUs)
    }

    @Test
    fun `applySession maps repeat-all and shuffle`() {
        val state = MprisState()

        val changed = state.applySession(sessionOf(repeatMode = RepeatMode.ALL, shuffled = true))

        assertEquals("Playlist", changed["LoopStatus"]?.value)
        assertEquals(true, changed["Shuffle"]?.value)
    }

    @Test
    fun `player properties expose capabilities and default status`() {
        val props = MprisState().properties(PLAYER_IFACE)

        assertEquals(true, props["CanControl"]?.value)
        assertEquals(true, props["CanSeek"]?.value)
        assertEquals("Stopped", props["PlaybackStatus"]?.value)
    }

    @Test
    fun `property of unknown interface is null`() {
        assertNull(MprisState().property("com.example.Bogus", "Whatever"))
    }

    @Test
    fun `loopStatusToRepeatMode maps the three MPRIS values`() {
        assertEquals(RepeatMode.ONE, loopStatusToRepeatMode("Track"))
        assertEquals(RepeatMode.ALL, loopStatusToRepeatMode("Playlist"))
        assertEquals(RepeatMode.OFF, loopStatusToRepeatMode("None"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun metadataOf(changed: Map<String, Variant<*>>): Map<String, Variant<*>> =
        changed.getValue("Metadata").value as Map<String, Variant<*>>

    private fun trackOf(
        id: Long,
        title: String,
        gameTitle: String? = null,
        artistName: String? = null,
        trackNumber: Int = 0,
    ): Track = Track(
        id = id,
        path = "/library/$title.psf",
        source = "test",
        title = title,
        trackLengthMs = 60_000L,
        trackNumber = trackNumber,
        fadeLengthMs = 0L,
        game = gameTitle?.let { Game(id = 1, title = it, photoUrl = null, artists = null, tracks = null) },
        artists = artistName?.let { listOf(Artist(id = 1, name = it, photoUrl = null, tracks = null, games = null)) },
        platform = Platform.OTHER,
    )

    private fun playbackOf(state: PlayerState, positionMs: Long = 0L): ChipboxPlaybackState =
        ChipboxPlaybackState(
            state = state,
            position = positionMs,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
        )

    private fun sessionOf(repeatMode: RepeatMode, shuffled: Boolean): Session = Session(
        type = SessionType.ALL_TRACKS,
        contentId = 0L,
        repeatMode = repeatMode,
        shuffled = shuffled,
    )
}
