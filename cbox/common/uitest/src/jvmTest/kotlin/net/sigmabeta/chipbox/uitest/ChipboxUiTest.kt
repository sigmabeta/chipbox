package net.sigmabeta.chipbox.uitest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.uitest.harness.StubStringProvider
import net.sigmabeta.chipbox.uitest.harness.createTestAppGraph
import net.sigmabeta.chipbox.uitest.harness.writeFailureArtifacts
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.perf.LocalLogger
import kotlin.reflect.KClass

/**
 * Entry point for the Chipbox UI test DSL. Hosts the real `ChipboxAppUi` shell over the fake
 * [net.sigmabeta.chipbox.uitest.harness.TestAppGraph] (pre-populated with a deterministic random
 * library) and exposes the verbs a test scripts against:
 *
 * ```
 * runChipboxUiTest {
 *     val gameId = seedGame("Metal Slug")
 *     startAtScreen(GameDetail(gameId))
 *     assertTitle("Metal Slug")
 * }
 * ```
 *
 * The specs live in `src/jvmTest` (so Android Studio shows + runs them) and are mirrored onto
 * `androidDeviceTest`, so the same scripts run on the desktop JVM and on-device. See
 * docs/architecture/ui-test-dsl.md.
 *
 * On any failure inside [block], the harness dumps a screenshot + semantics tree of the live scene
 * (paths logged) before rethrowing — see [writeFailureArtifacts].
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooGenericExceptionCaught") // any failure (AssertionError included) should dump artifacts
fun runChipboxUiTest(block: ChipboxUiTest.() -> Unit) = runComposeUiTest {
    val test = ChipboxUiTest(this)
    test.launchShell()
    try {
        test.block()
    } catch (failure: Throwable) {
        writeFailureArtifacts(failure, test.hatchet)
        throw failure
    }
}

@OptIn(ExperimentalTestApi::class)
class ChipboxUiTest internal constructor(private val compose: ComposeUiTest) {
    private val graph = createTestAppGraph()
    private val destinations = MutableSharedFlow<Any>(extraBufferCapacity = 1)
    private val navigations = mutableListOf<Any>()

    /** The harness logger (also the one screens log through), reused to announce failure artifacts. */
    internal val hatchet: Hatchet get() = graph.hatchet

    /** Host the real shell once, on the Home tab, over the test graph. */
    internal fun launchShell() {
        // metroViewModel<>() needs a ViewModelStoreOwner; the app gets it from the Compose Window /
        // Voyager per-screen store — neither exists here, so supply a plain one.
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalViewModelStoreOwner provides storeOwner,
                LocalChipboxStringProvider provides StubStringProvider,
                LocalLogger provides graph.hatchet,
            ) {
                ChipboxAppUi(
                    onOpenUrl = {},
                    onCopyToClipboard = { _, _ -> },
                    activeTabDestinations = destinations,
                    onNavigate = { navigations += it },
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * The first game in the pre-populated library (sorted by title, so it's stable for a given
     * seed). Lets a test drive a real screen without seeding its own fixture.
     */
    fun firstGame(): Game = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            // The id from the list, then the full game (with tracks/artists) by id. getAllGames has
            // a load-once flag that the shell may already have tripped without tracks, so the
            // per-id getGame (a fresh cold flow) is what reliably carries the tracks.
            val list = graph.memoryRepository.getAllGames(withTracks = false, withArtists = false)
                .first { it is Data.Succeeded }

            @Suppress("UNCHECKED_CAST")
            val id = (list as Data.Succeeded<List<Game>>).data.first().id

            val game = graph.memoryRepository.getGame(id, withTracks = true, withArtists = true)
                .first { it is Data.Succeeded }

            @Suppress("UNCHECKED_CAST")
            (game as Data.Succeeded<Game?>).data!!
        }
    }

    /** The id of the artist named [name] in the library (e.g. to assert a navigation target). */
    fun artistId(name: String): Long = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            val data = graph.memoryRepository
                .getAllArtists(withTracks = false, withGames = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Artist>>).data.first { it.name == name }.id
        }
    }

    /**
     * Add a game to the library and return its id. Convenience over the populated default — use it
     * when a test needs a screen with known content to assert on.
     */
    fun seedGame(
        title: String,
        tracks: List<String> = listOf("Track 1"),
        artists: List<String> = listOf("Composer"),
    ): Long = runBlocking {
        graph.memoryRepository.upsertGame(
            RawGame(
                title = title,
                photoUrl = null,
                folderKey = "seed/$title",
                folderSignature = "seed",
                tracks = tracks.mapIndexed { index, trackTitle ->
                    RawTrack(
                        path = "/$title/$index",
                        source = "",
                        title = trackTitle,
                        // Cycle artists across tracks — a game with >1 distinct artist renders its
                        // songs as NameCaptionValue rows (artist in the caption) instead of plain
                        // label/value rows.
                        artist = artists[index % artists.size],
                        game = title,
                        length = 120_000L,
                        trackNumber = index + 1,
                        fadeLengthMs = 0L,
                    )
                },
            ),
        ).gameId
    }

    /** Navigate to [destination] (a feature route key) inside the active tab, where the chrome is. */
    fun startAtScreen(destination: Any) {
        destinations.tryEmit(destination)
        compose.waitForIdle()
    }

    /** Click the [WideItem][net.sigmabeta.sage.components.WideItemListModel] row named [name]. */
    fun clickWideItem(name: String) = clickItem("WideItemListModel", name)

    /**
     * Click the [NameCaptionValueItem][net.sigmabeta.sage.components.NameCaptionValueListModel] row
     * named [name].
     */
    fun clickNameCaptionValueItem(name: String) = clickItem("NameCaptionValueListModel", name)

    // Select by item type (the model's testTag, set in ListModel.Content) AND displayed name, so
    // the typed verbs target the right kind of row even when two item types show the same text.
    private fun clickItem(typeTag: String, name: String) {
        compose.onNode(hasTestTag(typeTag) and hasText(name)).performClick()
        compose.waitForIdle()
    }

    /**
     * Click the (single) node displaying [text], regardless of item type — for when the row kind
     * doesn't matter (e.g. a song row that's a label/value or name/caption/value depending on the
     * game's artist count). Prefer the typed `click*Item` verbs when the type is meaningful.
     */
    fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** Assert the current screen's title (rendered in the chrome's top bar) is [text]. */
    fun assertTitle(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /** Assert a node displaying exactly [text] is shown (e.g. a list row's name). */
    fun assertDisplayed(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Assert the shell navigated to [destination] (a typed route key) at some point. Route keys are
     * data classes, so this matches on value — e.g. `assertNavigationEvent(ArtistDetail(3023))`.
     */
    fun assertNavigationEvent(destination: Any) {
        compose.waitForIdle()
        check(destination in navigations) {
            "Expected a navigation to $destination, but saw: $navigations"
        }
    }

    /**
     * Assert the [Director] received [request] (by value) — e.g.
     * `assertDirectorReceived(SessionRequest.Play)`. For requests carrying a value you don't want
     * to spell out (a `Start` with a whole `Session`), use the type-only overload below.
     */
    fun assertDirectorReceived(request: SessionRequest) {
        compose.waitForIdle()
        check(request in graph.fakeDirector.requests) {
            "Expected the director to receive $request, but saw: ${graph.fakeDirector.requests}"
        }
    }

    /** Assert the [Director] received any request of type [T] — e.g.
     *  `assertDirectorReceived<SessionRequest.Start>()` for "playback started". */
    inline fun <reified T : SessionRequest> assertDirectorReceived() =
        assertDirectorReceivedOfType(T::class)

    fun assertDirectorReceivedOfType(type: KClass<out SessionRequest>) {
        compose.waitForIdle()
        val received = graph.fakeDirector.requests
        check(received.any { type.isInstance(it) }) {
            "Expected the director to receive a ${type.simpleName} request, but saw: $received"
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
