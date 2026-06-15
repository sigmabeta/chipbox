package net.sigmabeta.chipbox.uitest

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.common.ui.chrome.api.TitleBarController
import net.sigmabeta.chipbox.features.gamedetail.GameDetailRoute
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.ui.theme.api.ChipboxTheme
import net.sigmabeta.chipbox.uitest.harness.createTestAppGraph
import net.sigmabeta.sage.ui.perf.LocalLogger
import kotlin.test.Test

/**
 * Phase 1, step 1: prove the hard machinery on a single real screen — Metro builds the real
 * [GameDetailViewModel] (assisted, resolved through the ViewModelGraph factory) over a seeded
 * [MemoryRepository], rendered in `runComposeUiTest`, and the entity data surfaces in the
 * semantics tree. The full tabs shell + Voyager `startAtScreen`/`assertNavigationEvent` wrap is
 * the next step. See docs/architecture/ui-test-dsl.md.
 */
@OptIn(ExperimentalTestApi::class)
class GameDetailHarnessTest {
    @Test
    fun seededGameRendersTitleAndContent() = runComposeUiTest {
        val graph = createTestAppGraph()

        // Seed the in-memory repo and use the id it assigns (MemoryRepository auto-keys games).
        val gameId = runBlocking {
            graph.memoryRepository.upsertGame(
                RawGame(
                    title = "Metal Slug",
                    photoUrl = null,
                    folderKey = "test-folder",
                    folderSignature = "sig",
                    tracks = listOf(
                        RawTrack(
                            path = "/metal-slug/stage1",
                            source = "",
                            title = "Stage 1",
                            artist = "JIM",
                            game = "Metal Slug",
                            length = 120_000L,
                            trackNumber = 1,
                            fadeLengthMs = 0L,
                        ),
                    ),
                ),
            ).gameId
        }

        // metroViewModel<>() needs a ViewModelStoreOwner; the app gets it from the Compose Window /
        // Voyager per-screen store — neither exists here, so supply a plain one.
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val titleBarController = TitleBarController()

        setContent {
            ChipboxTheme {
                CompositionLocalProvider(
                    LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                    LocalViewModelStoreOwner provides storeOwner,
                    LocalTitleBarController provides titleBarController,
                    LocalChipboxStringProvider provides graph.stringProvider,
                    LocalLogger provides graph.hatchet,
                ) {
                    Column {
                        // The screen pushes its title to the TitleBarController (no top bar here),
                        // so render it as a node the assertion can find.
                        Text(titleBarController.state.title.orEmpty())
                        GameDetailRoute(gameId = gameId, onEvent = {})
                    }
                }
            }
        }

        waitForIdle()

        onNodeWithText("Metal Slug").assertIsDisplayed()
        onNodeWithText("Stage 1").assertIsDisplayed()
    }
}
