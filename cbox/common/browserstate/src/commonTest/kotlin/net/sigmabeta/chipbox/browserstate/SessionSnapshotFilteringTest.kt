package net.sigmabeta.chipbox.browserstate

import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pre-restore filtering: browser state stores track ids, and RSN extraction or a rebuilt library
 * can invalidate them. A restore must drop unknown tracks, keep the queue's order, point at the
 * nearest surviving track, and give up entirely when nothing is left.
 */
class SessionSnapshotFilteringTest {

    @Test
    fun `known tracks keep the queue and playback state untouched`() {
        val saved = session(queue = listOf(10L, 11L, 12L), current = 11L)

        val filtered = saved.withKnownTracks(setOf(10L, 11L, 12L))

        assertEquals(saved, filtered)
    }

    @Test
    fun `unknown queue entries are dropped and the current track is remapped by index`() {
        val saved = session(queue = listOf(10L, 11L, 12L), current = 11L)

        val filtered = saved.withKnownTracks(setOf(10L, 12L))!!

        assertEquals(listOf(10L, 12L), filtered.resolvedSetlist)
        assertEquals(listOf(10L, 12L), filtered.explicitSetlist)
        assertEquals(12L, filtered.currentTrackId, "the old index 1 now points at 12")
        assertEquals(9_000L, filtered.positionMs)
        assertEquals(true, filtered.shuffled)
        assertEquals(RepeatMode.ALL, filtered.repeatMode)
    }

    @Test
    fun `an unknown current track near the end clamps to the last survivor`() {
        val saved = session(queue = listOf(10L, 11L, 12L), current = 12L)

        val filtered = saved.withKnownTracks(setOf(10L, 11L))!!

        assertEquals(listOf(10L, 11L), filtered.resolvedSetlist)
        assertEquals(11L, filtered.currentTrackId)
    }

    @Test
    fun `explicit setlists are filtered too`() {
        val saved = SessionSnapshot(
            type = SessionType.SETLIST,
            contentId = 0L,
            explicitSetlist = listOf(10L, 11L),
            currentTrackId = 11L,
        )

        val filtered = saved.withKnownTracks(setOf(10L))!!

        assertEquals(listOf(10L), filtered.explicitSetlist)
        assertEquals(10L, filtered.currentTrackId)
    }

    @Test
    fun `a missing current track falls back to the first survivor`() {
        val saved = session(queue = listOf(10L, 11L), current = null)

        val filtered = saved.withKnownTracks(setOf(11L))!!

        assertEquals(11L, filtered.currentTrackId)
    }

    @Test
    fun `a legacy snapshot with only a current track is kept when known`() {
        val legacy = SessionSnapshot(
            type = SessionType.GAME,
            contentId = 7L,
            currentTrackId = 42L,
            positionMs = 12_000L,
        )

        val filtered = legacy.withKnownTracks(setOf(42L))!!

        assertEquals(42L, filtered.currentTrackId)
        assertEquals(12_000L, filtered.positionMs)
        assertNull(filtered.resolvedSetlist)
    }

    @Test
    fun `a legacy snapshot whose current track vanished is dropped`() {
        val legacy = SessionSnapshot(type = SessionType.GAME, contentId = 7L, currentTrackId = 404L)

        assertNull(legacy.withKnownTracks(setOf(1L, 2L)))
    }

    @Test
    fun `a snapshot whose tracks all vanished is dropped`() {
        val saved = session(queue = listOf(10L, 11L), current = 10L)

        assertNull(saved.withKnownTracks(setOf(99L)))
    }

    @Test
    fun `a single track session survives only when its content id is known`() {
        val saved = SessionSnapshot(
            type = SessionType.SINGLE_TRACK,
            contentId = 55L,
            currentTrackId = null,
        )

        assertEquals(55L, saved.withKnownTracks(setOf(55L))?.currentTrackId)
        assertNull(saved.withKnownTracks(setOf(1L)))
    }

    private fun session(queue: List<Long>, current: Long?) = SessionSnapshot(
        type = SessionType.SETLIST,
        contentId = 0L,
        explicitSetlist = queue,
        resolvedSetlist = queue,
        currentTrackId = current,
        shuffled = true,
        repeatMode = RepeatMode.ALL,
        positionMs = 9_000L,
        modified = true,
    )
}
