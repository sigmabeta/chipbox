package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SessionType
import kotlin.test.Test

/**
 * The Playlist detail screen: its content + view-mode playback CTAs (Play All / Shuffle / tap-a-track,
 * all of which start a [SessionType.PLAYLIST] session resolved by the director from the playlist id),
 * entering edit mode, and the empty state.
 *
 * Playlists are seeded over real, distinctly-titled library tracks so the rows render and can be
 * asserted/clicked unambiguously.
 */
class PlaylistDetailTest {

    private fun ChipboxUiTest.threeLibraryTracks(): List<Track> =
        libraryTracks().distinctBy { it.title }.take(3).also { check(it.size == 3) }

    @Test
    fun rendersTracksAndTheViewModeCtas() = runChipboxUiTest {
        val tracks = threeLibraryTracks()
        val id = seedPlaylist("My Mix", tracks.map { it.id })

        startAtScreen(PlaylistDetail(id))
        waitForContent(tracks[0].title)

        assertCtaDisplayed("Play All")
        assertCtaDisplayed("Shuffle")
        assertCtaDisplayed("Edit playlist")
        assertNameCaptionValueItemDisplayed(tracks[0].title)
        assertNameCaptionValueItemDisplayed(tracks[2].title)
    }

    @Test
    fun playAllStartsThePlaylistFromTheTop() = runChipboxUiTest {
        val tracks = threeLibraryTracks()
        val id = seedPlaylist("My Mix", tracks.map { it.id })
        startAtScreen(PlaylistDetail(id))
        waitForContent(tracks[0].title)

        clickCta("Play All")

        assertStartedSession("the playlist from the top") {
            it.type == SessionType.PLAYLIST && it.contentId == id && !it.shuffled && it.startingPosition == 0
        }
    }

    @Test
    fun shuffleStartsAShuffledPlaylistSession() = runChipboxUiTest {
        val tracks = threeLibraryTracks()
        val id = seedPlaylist("My Mix", tracks.map { it.id })
        startAtScreen(PlaylistDetail(id))
        waitForContent(tracks[0].title)

        clickCta("Shuffle")

        assertStartedSession("a shuffled playlist session") {
            it.type == SessionType.PLAYLIST && it.contentId == id && it.shuffled
        }
    }

    @Test
    fun tappingATrackStartsThePlaylistFromThatPosition() = runChipboxUiTest {
        val tracks = threeLibraryTracks()
        val id = seedPlaylist("My Mix", tracks.map { it.id })
        startAtScreen(PlaylistDetail(id))
        waitForContent(tracks[2].title)

        clickNameCaptionValueItem(tracks[2].title)

        assertStartedSession("the playlist from the third track") {
            it.type == SessionType.PLAYLIST && it.contentId == id && it.startingPosition == 2
        }
    }

    @Test
    fun editPlaylistCtaEntersEditMode() = runChipboxUiTest {
        val tracks = threeLibraryTracks()
        val id = seedPlaylist("My Mix", tracks.take(1).map { it.id })
        startAtScreen(PlaylistDetail(id))
        waitForContent(tracks[0].title)

        clickCta("Edit playlist")

        // The view-mode header is swapped for the manage CTAs.
        assertCtaDisplayed("Done")
        assertCtaDisplayed("Rename playlist")
        assertCtaDisplayed("Delete playlist")
    }

    @Test
    fun emptyPlaylistShowsTheEmptyState() = runChipboxUiTest {
        val id = seedPlaylist("Empty Mix")

        startAtScreen(PlaylistDetail(id))

        assertEmptyStateDisplayed(
            "Find a song, game, or artist, and click \"Add to Playlist\" to get started.",
        )
    }
}
