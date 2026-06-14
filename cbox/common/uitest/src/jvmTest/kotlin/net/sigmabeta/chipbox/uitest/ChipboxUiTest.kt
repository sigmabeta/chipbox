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
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.uitest.harness.StubHatchet
import net.sigmabeta.chipbox.uitest.harness.StubStringProvider
import net.sigmabeta.chipbox.uitest.harness.createTestAppGraph
import net.sigmabeta.sage.ui.perf.LocalLogger

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
 * jvmTest-only for now; it'll move into the shared `uiTest` dir (with a per-target graph builder)
 * once the verb set settles, so the same scripts run on-device too. See
 * docs/architecture/ui-test-dsl.md.
 */
@OptIn(ExperimentalTestApi::class)
fun runChipboxUiTest(block: ChipboxUiTest.() -> Unit) = runComposeUiTest {
    ChipboxUiTest(this).apply {
        launchShell()
        block()
    }
}

@OptIn(ExperimentalTestApi::class)
class ChipboxUiTest internal constructor(private val compose: ComposeUiTest) {
    private val graph = createTestAppGraph()
    private val destinations = MutableSharedFlow<Any>(extraBufferCapacity = 1)

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
                LocalLogger provides StubHatchet,
            ) {
                ChipboxAppUi(
                    onOpenUrl = {},
                    onCopyToClipboard = { _, _ -> },
                    activeTabDestinations = destinations,
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
            val data = graph.memoryRepository
                .getAllGames(withTracks = false, withArtists = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Game>>).data.first()
        }
    }

    /**
     * Add a game to the library and return its id. Convenience over the populated default — use it
     * when a test needs a screen with known content to assert on.
     */
    fun seedGame(
        title: String,
        tracks: List<String> = listOf("Track 1"),
        artist: String = "Composer",
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
                        artist = artist,
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

    /** Assert the current screen's title (rendered in the chrome's top bar) is [text]. */
    fun assertTitle(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /** Assert a node displaying exactly [text] is shown (e.g. a list row's name). */
    fun assertDisplayed(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
