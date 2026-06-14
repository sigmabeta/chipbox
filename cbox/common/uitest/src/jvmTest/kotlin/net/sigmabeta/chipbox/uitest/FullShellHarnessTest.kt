package net.sigmabeta.chipbox.uitest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.uitest.harness.StubHatchet
import net.sigmabeta.chipbox.uitest.harness.StubStringProvider
import net.sigmabeta.chipbox.uitest.harness.createTestAppGraph
import net.sigmabeta.sage.ui.perf.LocalLogger
import kotlin.test.Test

/**
 * Phase 1, step 3: host the *full* `ChipboxAppUi` tabs shell over the fake [TestAppGraph] and open
 * a real screen *inside the active tab* via the `activeTabDestinations` seam — the same place an
 * in-tab `NavigateTo` lands — so the screen renders with the tabs chrome (TopAppBar) around it.
 * Both the seeded title ("Metal Slug", in the top bar) and the screen content ("JIM"/"Stage 1")
 * are then present. This is the navigation path the `startAtScreen` DSL verb will build on. See
 * docs/architecture/ui-test-dsl.md.
 */
@OptIn(ExperimentalTestApi::class)
class FullShellHarnessTest {
    @Test
    fun startingAtSeededGameShowsTitleAndContent() = runComposeUiTest {
        val graph = createTestAppGraph()
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
        val destinations = MutableSharedFlow<Any>(extraBufferCapacity = 1)

        setContent {
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
        waitForIdle()

        // Open GameDetail inside the active tab — the seam pushes it onto that tab's Navigator.
        destinations.tryEmit(GameDetail(gameId))
        waitForIdle()

        // Title (top bar, from the tabs chrome) + screen content both render.
        onNodeWithText("Metal Slug").assertIsDisplayed()
        onNodeWithText("JIM").assertIsDisplayed()
        onNodeWithText("Stage 1").assertIsDisplayed()
    }
}
