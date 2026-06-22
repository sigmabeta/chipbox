package net.sigmabeta.chipbox.features.playlistdetail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.EditTextListModel
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `membership ids hydrate into full tracks, in playlist order`() = runTest {
        val playlists = FakePlaylistsRepository()
        val id = playlists.seed("Mix", listOf(2L, 1L))
        val repository = FakeRepository(
            mapOf(
                1L to trackOf(1L, "First"),
                2L to trackOf(2L, "Second"),
            ),
        )
        val vm = PlaylistDetailViewModel(id, repository, playlists, stubStringProvider(), BluntHatchet())

        val state = vm.state.first { (it.tracks as? LCE.Content)?.data?.isNotEmpty() == true }
        // Order follows the playlist's positions (2 then 1), not the id order.
        assertEquals(listOf("Second", "First"), (state.tracks as LCE.Content).data.map { it.title })
        assertEquals("Mix", (state.playlist as LCE.Content).data.name)
    }

    @Test
    fun `a missing playlist surfaces the not-found state`() = runTest {
        val vm = PlaylistDetailViewModel(
            playlistId = 99L,
            repository = FakeRepository(emptyMap()),
            playlists = FakePlaylistsRepository(),
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
        )

        val state = vm.state.first { it.notFound }
        assertTrue(state.notFound)
    }

    @Test
    fun `EditClicked and DoneClicked toggle edit mode`() = runTest {
        val vm = vmWithTracks(listOf(1L))
        assertFalse(vm.state.value.isEditing)

        vm.sendAction(PlaylistDetailAction.EditClicked)
        assertTrue(vm.state.value.isEditing)

        vm.sendAction(PlaylistDetailAction.DoneClicked)
        assertFalse(vm.state.value.isEditing)
    }

    @Test
    fun `TrackRemoved persists the playlist without that track`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L, 2L, 3L))
        vm.state.first { (it.tracks as? LCE.Content)?.data?.size == 3 }

        vm.sendAction(PlaylistDetailAction.TrackRemoved(2L))

        assertEquals(listOf(1L, 3L), playlists.trackIds(id).first())
    }

    @Test
    fun `Reorder maps list indices past the edit header and persists the new order`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L, 2L, 3L))
        vm.state.first { (it.tracks as? LCE.Content)?.data?.size == 3 }

        // Edit mode renders 3 CTA rows then the tracks, so the first track is list index 3. Move it
        // (list index 3 → 5) to the end.
        vm.sendAction(SageAction.Reorder(fromIndex = 3, toIndex = 5))

        assertEquals(listOf(2L, 3L, 1L), playlists.trackIds(id).first())
    }

    @Test
    fun `DeleteClicked opens the confirmation but does not delete yet`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L))
        vm.state.first { (it.tracks as? LCE.Content)?.data?.size == 1 }

        vm.sendAction(PlaylistDetailAction.DeleteClicked)

        assertTrue(vm.state.value.isConfirmingDelete)
        assertTrue(playlists.playlists().first().any { it.id == id }, "playlist should still exist")
    }

    @Test
    fun `confirming the delete removes the playlist`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L))
        vm.state.first { (it.tracks as? LCE.Content)?.data?.size == 1 }
        vm.sendAction(PlaylistDetailAction.DeleteClicked)

        vm.sendAction(SageAction.ConfirmationConfirmed(id = 0L))

        assertTrue(playlists.playlists().first().none { it.id == id })
    }

    @Test
    fun `cancelling the delete keeps the playlist and closes the prompt`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L))
        vm.state.first { (it.tracks as? LCE.Content)?.data?.size == 1 }
        vm.sendAction(PlaylistDetailAction.DeleteClicked)

        vm.sendAction(SageAction.ConfirmationCancelled(id = 0L))

        assertFalse(vm.state.value.isConfirmingDelete)
        assertTrue(playlists.playlists().first().any { it.id == id })
    }

    @Test
    fun `RenameClicked opens the inline rename field`() = runTest {
        val vm = vmWithTracks(listOf(1L))

        vm.sendAction(PlaylistDetailAction.RenameClicked)

        assertTrue(vm.state.value.isRenaming)
    }

    @Test
    fun `EditTextSubmitted renames the playlist and closes the field`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L))
        vm.sendAction(PlaylistDetailAction.RenameClicked)

        vm.sendAction(SageAction.EditTextSubmitted(id = 0L, text = "Renamed"))

        assertFalse(vm.state.value.isRenaming)
        assertEquals("Renamed", playlists.playlist(id).first()?.name)
    }

    @Test
    fun `EditTextCancelled closes the rename field without renaming`() = runTest {
        val (vm, playlists, id) = seededVm(listOf(1L))
        vm.sendAction(PlaylistDetailAction.RenameClicked)

        vm.sendAction(SageAction.EditTextCancelled(id = 0L))

        assertFalse(vm.state.value.isRenaming)
        assertEquals("Mix", playlists.playlist(id).first()?.name)
    }

    @Test
    fun `rename field prefill is blank for a default-named playlist, otherwise the current name`() {
        // The stub StringProvider returns the enum name, so the default base is "PLAYLISTS_DEFAULT_NAME".
        assertEquals("", renameFieldFor("PLAYLISTS_DEFAULT_NAME").initialText)
        assertEquals("", renameFieldFor("PLAYLISTS_DEFAULT_NAME 3").initialText)
        assertEquals("Chill Mix", renameFieldFor("Chill Mix").initialText)
    }

    private fun renameFieldFor(name: String): EditTextListModel {
        val state = PlaylistDetailState(
            playlist = LCE.Content(Playlist(id = 1L, name = name, trackCount = 0, createdAtMs = 0L)),
            isEditing = true,
            isRenaming = true,
        )
        return state.toListItems(stubStringProvider()).filterIsInstance<EditTextListModel>().single()
    }

    // ---- helpers ----

    private data class Seeded(
        val vm: PlaylistDetailViewModel,
        val playlists: FakePlaylistsRepository,
        val id: Long,
    )

    private fun seededVm(trackIds: List<Long>): Seeded {
        val playlists = FakePlaylistsRepository()
        val id = playlists.seed("Mix", trackIds)
        val repository = FakeRepository(trackIds.associateWith { trackOf(it, "Track $it") })
        val vm = PlaylistDetailViewModel(id, repository, playlists, stubStringProvider(), BluntHatchet())
        return Seeded(vm, playlists, id)
    }

    private fun vmWithTracks(trackIds: List<Long>): PlaylistDetailViewModel = seededVm(trackIds).vm

    private fun trackOf(id: Long, title: String): Track = Track(
        id = id,
        path = "/$title",
        source = "",
        title = title,
        trackLengthMs = 60_000L,
        trackNumber = 1,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        platform = Platform.OTHER,
    )

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
