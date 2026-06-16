package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * The Now Playing screen's in-screen "ContextMenu" that replaces the track-info block. Tapping the
 * track info opens LINKS (game + artist jump-offs); the menu button opens CONTROLS (repeat/shuffle
 * rows that toggle on tap); the back row returns to NONE; the setlist button is a placeholder.
 *
 * Every test seeds a live session via [startNowPlaying] first — the screen bounces straight back
 * when the player is idle — then drives the real menu over the [net.sigmabeta.chipbox.player.director.fake.FakeDirector].
 */
class NowPlayingTest {

    @Test
    fun trackInfoTapOpensLinksGameRowNavigates() = runChipboxUiTest {
        startNowPlaying(track = singleArtistTrack(), session = allTracksSession())

        clickTag(TRACK_INFO_TAG) // open LINKS
        clickTag(CTX_GAME_TAG) // game row

        assertNavigationEvent(GameDetail(gameId(GAME_TITLE)))
    }

    @Test
    fun linksArtistRowOpensArtistDetailForOneArtist() = runChipboxUiTest {
        startNowPlaying(track = singleArtistTrack(), session = allTracksSession())

        clickTag(TRACK_INFO_TAG) // open LINKS
        clickTag(CTX_ARTISTS_TAG) // single artist → navigate straight through

        assertNavigationEvent(ArtistDetail(artistId(ARTIST_JAKE)))
    }

    @Test
    fun linksArtistRowExpandsToArtistsForSeveralArtists() = runChipboxUiTest {
        startNowPlaying(track = twoArtistTrack(), session = allTracksSession())

        clickTag(TRACK_INFO_TAG)
        // A multi-artist track's artist link opens the ARTISTS picker rather than navigating.
        clickTag(CTX_ARTISTS_TAG)
        clickTag(ctxArtistTag(artistId(ARTIST_MICHIKO)))

        assertNavigationEvent(ArtistDetail(artistId(ARTIST_MICHIKO)))
    }

    @Test
    fun menuButtonOpensControls() = runChipboxUiTest {
        startNowPlaying(track = singleArtistTrack(), session = allTracksSession())

        clickTag(MENU_BUTTON_TAG)

        // The CONTROLS rows render the real human-readable repeat/shuffle strings.
        assertTextInRow(REPEAT_OFF_LABEL)
        assertTextInRow(SHUFFLE_OFF_LABEL)
    }

    @Test
    fun controlsRepeatRowAdvancesRepeatMode() = runChipboxUiTest {
        startNowPlaying(
            track = singleArtistTrack(),
            session = allTracksSession(repeatMode = RepeatMode.OFF),
        )

        clickTag(MENU_BUTTON_TAG)
        clickTag(CTX_REPEAT_TAG)

        // OFF cycles to ALL.
        assertDirectorReceived(SessionRequest.SetRepeatMode(RepeatMode.ALL))
    }

    @Test
    fun controlsShuffleRowTogglesShuffle() = runChipboxUiTest {
        startNowPlaying(
            track = singleArtistTrack(),
            session = allTracksSession(shuffled = false),
        )

        clickTag(MENU_BUTTON_TAG)
        clickTag(CTX_SHUFFLE_TAG)

        assertDirectorReceived(SessionRequest.SetShuffled(true))
    }

    @Test
    fun contextMenuBackRowReturnsToTrackInfo() = runChipboxUiTest {
        startNowPlaying(track = singleArtistTrack(), session = allTracksSession())

        clickTag(MENU_BUTTON_TAG)
        assertTextInRow(REPEAT_OFF_LABEL)

        clickTag(CTX_BACK_TAG)

        assertTextNotInRow(REPEAT_OFF_LABEL)
    }

    @Test
    fun setlistButtonDoesNotOpenControls() = runChipboxUiTest {
        startNowPlaying(track = singleArtistTrack(), session = allTracksSession())

        clickTag(SETLIST_BUTTON_TAG)

        // Placeholder for the future Setlist feature: it shows a snackbar (auto-dismissed under the
        // test clock, so not asserted here) and must not behave like the adjacent menu button.
        assertTextNotInRow(SHUFFLE_OFF_LABEL)
    }

    // ---- fixtures ----

    // Seed a live session via the generic harness verb, then open Now Playing on top of it.
    private fun ChipboxUiTest.startNowPlaying(track: Track, session: Session) {
        startFromSession(session = session, track = track)
        startAtScreen(NowPlaying)
    }

    private fun ChipboxUiTest.singleArtistTrack(): Track = ironQuestTrack(listOf(artistOf(ARTIST_JAKE)))

    private fun ChipboxUiTest.twoArtistTrack(): Track =
        ironQuestTrack(listOf(artistOf(ARTIST_JAKE), artistOf(ARTIST_MICHIKO)))

    // A synthetic now-playing track that points at real library ids, so the game/artist links
    // navigate to detail screens the harness can render.
    private fun ChipboxUiTest.ironQuestTrack(artists: List<Artist>): Track {
        val ironQuestId = gameId(GAME_TITLE)
        return Track(
            id = 1L,
            path = "/iron-quest/$TRACK_TITLE",
            source = "test",
            title = TRACK_TITLE,
            trackLengthMs = 154_000L,
            trackNumber = 1,
            fadeLengthMs = 0L,
            game = Game(id = ironQuestId, title = GAME_TITLE, photoUrl = null, artists = null, tracks = null),
            artists = artists,
            platform = Platform.OTHER,
            gameId = ironQuestId,
        )
    }

    private fun ChipboxUiTest.artistOf(name: String): Artist =
        Artist(id = artistId(name), name = name, photoUrl = null, tracks = null, games = null)

    private fun allTracksSession(
        repeatMode: RepeatMode = RepeatMode.OFF,
        shuffled: Boolean = false,
    ): Session = Session(
        type = SessionType.ALL_TRACKS,
        contentId = 0L,
        repeatMode = repeatMode,
        shuffled = shuffled,
    )

    // Per-artist ARTISTS-row tag, keyed by artist id. Mirrors nowPlayingCtxArtistTag in
    // NowPlayingContent.kt.
    private fun ctxArtistTag(artistId: Long) = "NowPlayingCtxArtist:$artistId"

    private companion object {
        const val GAME_TITLE = "Iron Quest"
        const val TRACK_TITLE = "Castle 36"
        const val ARTIST_JAKE = "Jake Shimomura"
        const val ARTIST_MICHIKO = "Michiko Tanaka"

        // The real CONTROLS-row strings for an off/sequential session (strings-now-playing.xml).
        const val REPEAT_OFF_LABEL = "Playing sequentially"
        const val SHUFFLE_OFF_LABEL = "Playing in order"

        // Kept in sync with the NOW_PLAYING_*_TAG constants in NowPlayingContent.kt.
        const val TRACK_INFO_TAG = "NowPlayingTrackInfo"
        const val MENU_BUTTON_TAG = "NowPlayingMenuButton"
        const val SETLIST_BUTTON_TAG = "NowPlayingSetlistButton"
        const val CTX_BACK_TAG = "NowPlayingCtxBack"
        const val CTX_GAME_TAG = "NowPlayingCtxGame"
        const val CTX_ARTISTS_TAG = "NowPlayingCtxArtists"
        const val CTX_REPEAT_TAG = "NowPlayingCtxRepeat"
        const val CTX_SHUFFLE_TAG = "NowPlayingCtxShuffle"
    }
}
