package net.sigmabeta.chipbox.features.tracksforartist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TracksForArtistViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `artist name from the assisted arg seeds the title state`() = runTest {
        val vm = newViewModel(artistName = "Nobuo Uematsu")
        assertEquals("Nobuo Uematsu", vm.state.first().artistName)
    }

    @Test
    fun `loaded tracks surface as LCE Content`() = runTest {
        val tracks = listOf(trackOf(1, "Prelude"), trackOf(2, "Awakening"))
        val vm = newViewModel(repository = repoWithTracks(tracks))
        val state = vm.state.first { (it.tracks as? LCE.Content)?.data?.isNotEmpty() == true }
        assertEquals(LCE.Content(tracks), state.tracks)
    }

    @Test
    fun `an artist with no tracks surfaces as LCE Content with an empty list`() = runTest {
        val vm = newViewModel(repository = repoWithTracks(emptyList()))
        val state = vm.state.first { it.tracks is LCE.Content }
        assertEquals(LCE.Content(emptyList<Track>()), state.tracks)
    }

    @Test
    fun `a repository failure surfaces as LCE Error with the documented op tag`() = runTest {
        val vm = newViewModel(
            repository = object : Repository by FakeRepository(emptyMap()) {
                override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> =
                    error("io")
            },
        )
        val state = vm.state.first { it.tracks is LCE.Error }
        val err = state.tracks as LCE.Error
        assertEquals("tracks_for_artist.load", err.operationName)
        assertEquals("io", err.error.message)
    }

    @Test
    fun `PlayAllClicked starts an ARTIST session at position 0`() = runTest {
        val director = FakeDirector()
        val vm = newViewModel(director = director)
        vm.sendAction(TracksForArtistAction.PlayAllClicked)
        val start = director.requests.filterIsInstance<SessionRequest.Start>().single().session
        assertEquals(SessionType.ARTIST, start.type)
        assertEquals(ARTIST_ID, start.contentId)
        assertEquals(0, start.startingPosition)
        assertTrue(!start.shuffled)
    }

    @Test
    fun `TrackClicked starts the session at the tapped position`() = runTest {
        val director = FakeDirector()
        val vm = newViewModel(director = director)
        vm.sendAction(TracksForArtistAction.TrackClicked(position = 7))
        val start = director.requests.filterIsInstance<SessionRequest.Start>().single().session
        assertEquals(7, start.startingPosition)
    }

    @Test
    fun `ShuffleAllClicked starts a shuffled ARTIST session`() = runTest {
        val director = FakeDirector()
        val vm = newViewModel(director = director)
        vm.sendAction(TracksForArtistAction.ShuffleAllClicked)
        val start = director.requests.filterIsInstance<SessionRequest.Start>().single().session
        assertTrue(start.shuffled)
        assertEquals(SessionType.ARTIST, start.type)
    }

    // ---- helpers ----

    private fun newViewModel(
        artistName: String = "Artist",
        repository: Repository = repoWithTracks(emptyList()),
        director: FakeDirector = FakeDirector(),
    ) = TracksForArtistViewModel(
        artistId = ARTIST_ID,
        artistName = artistName,
        repository = repository,
        director = director,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private fun repoWithTracks(tracks: List<Track>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> = tracks
        }

    private fun trackOf(id: Long, title: String): Track = Track(
        id = id,
        path = "/t/$id",
        source = "",
        title = title,
        trackLengthMs = 1_000L,
        trackNumber = id.toInt(),
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.SNES,
    )

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val ARTIST_ID = 42L
    }
}
