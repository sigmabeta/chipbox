package net.sigmabeta.chipbox.features.library

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.playlists.Playlists
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [LibraryViewModel] is a pure-navigation ViewModel: each of the 6 menu actions emits the
 * matching [NavigateTo] event. Locks that mapping in.
 *
 * Events are emitted on a `MutableSharedFlow(replay = 0, extraBufferCapacity = 1,
 * onBufferOverflow = DROP_OLDEST)`, so a collector started AFTER `sendAction` may miss the
 * event entirely. We start the collection via `async(start = UNDISPATCHED) { events.first() }`
 * which subscribes synchronously before suspending, then trigger the action and `await` the
 * emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `FavoritesClicked emits NavigateTo Favorites`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.FavoritesClicked)
        assertEquals(NavigateTo(Favorites), event)
    }

    @Test
    fun `PlaylistsClicked emits NavigateTo Playlists`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.PlaylistsClicked)
        assertEquals(NavigateTo(Playlists()), event)
    }

    @Test
    fun `BrowseByGameClicked emits NavigateTo BrowseByGame`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.BrowseByGameClicked)
        assertEquals(NavigateTo(BrowseByGame), event)
    }

    @Test
    fun `BrowseByArtistClicked emits NavigateTo BrowseByArtist`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.BrowseByArtistClicked)
        assertEquals(NavigateTo(BrowseByArtist), event)
    }

    @Test
    fun `BrowseByPlatformClicked emits NavigateTo BrowseByPlatform`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.BrowseByPlatformClicked)
        assertEquals(NavigateTo(BrowseByPlatform), event)
    }

    @Test
    fun `BrowseAllTracksClicked emits NavigateTo BrowseAllTracks`() = runTest {
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val event = collectAndDispatch(vm, LibraryAction.BrowseAllTracksClicked)
        assertEquals(NavigateTo(BrowseAllTracks), event)
    }

    @Test
    fun `the initial uiStateActual carries the 6 menu items in declared order`() = runTest {
        // stateIn(Eagerly) on viewModelScope produces a populated uiStateActual immediately.
        // Asserts the row order/title isn't accidentally reshuffled by a future state tweak.
        val vm = LibraryViewModel(stubStringProvider(), BluntHatchet())
        val rendered = vm.uiStateActual.first()
        assertEquals(6, rendered.listItems.size, "menu should expose 6 rows")
    }

    // ---- helpers ----

    /** Subscribe to events first, dispatch the action, await the resulting event. */
    private suspend fun kotlinx.coroutines.CoroutineScope.collectAndDispatch(
        vm: LibraryViewModel,
        action: SageAction,
    ) = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    /** StringProvider that returns the SageStringId's `toString` — keeps assertions
     *  string-agnostic and avoids depending on the chipbox-strings compose-resources file. */
    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
