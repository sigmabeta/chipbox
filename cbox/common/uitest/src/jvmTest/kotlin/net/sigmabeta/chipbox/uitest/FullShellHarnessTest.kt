package net.sigmabeta.chipbox.uitest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUiViewModel
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
 * Phase 1, step 2: host the *full* `ChipboxAppUi` tabs shell over the fake [TestAppGraph] and
 * navigate to a real screen — fire `ChipboxEvent.NavigateTo` into the shell's own
 * `ChipboxAppUiViewModel`, whose effect collector runs the real `navigator.push(screenFor(..))`.
 * The seeded `GameDetail` then renders with its real content ("JIM", "Stage 1").
 *
 * The shell resolves its app VM from the [LocalViewModelStoreOwner] provided here, so the test
 * pulls that same instance back out of the store (via [ViewModelProvider]) to drive navigation —
 * the seam a future `startAtScreen` / `assertNavigationEvent` taps.
 *
 * NOTE: the app VM routes `NavigateTo` to the *outer* Navigator, which unmounts the tabs scaffold
 * (and its TopAppBar) — so the title bar isn't present here. A faithful `assertTitle` needs the
 * screen pushed onto the *active tab's* navigator (where the chrome lives); that in-tab navigation
 * seam is step 3. See docs/architecture/ui-test-dsl.md.
 */
@OptIn(ExperimentalTestApi::class)
class FullShellHarnessTest {
    @Test
    fun navigatingToSeededGameRendersItsContent() = runComposeUiTest {
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

        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalViewModelStoreOwner provides storeOwner,
                LocalChipboxStringProvider provides StubStringProvider,
                LocalLogger provides StubHatchet,
            ) {
                ChipboxAppUi(onOpenUrl = {}, onCopyToClipboard = { _, _ -> })
            }
        }
        waitForIdle()

        // The shell resolved its ChipboxAppUiViewModel into `storeOwner`; pull the same instance
        // back out and fire a navigation — its effect collector does the real Voyager push.
        val appViewModel = ViewModelProvider.create(storeOwner, graph.metroViewModelFactory)[
            ChipboxAppUiViewModel::class,
        ]
        appViewModel.handleEvent(ChipboxEvent.NavigateTo(GameDetail(gameId)))
        waitForIdle()

        // The real GameDetail screen rendered over the test graph: its artist + track are present.
        onNodeWithText("JIM").assertIsDisplayed()
        onNodeWithText("Stage 1").assertIsDisplayed()
    }
}
