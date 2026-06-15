package net.sigmabeta.chipbox.uitest

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Phase 0 spike: proves `runComposeUiTest` — Compose Multiplatform's cross-platform UI-test
 * engine — actually hosts a composable and queries the semantics tree on this project's
 * toolchain. Green here means the rails the full UI test DSL stands on are real.
 *
 * Lives in the shared `uiTest` source set (NOT `commonTest`), so the one spec runs on two
 * targets: the JVM desktop host (`:jvmTest`) and a real Android device (`:androidDeviceTest`,
 * on-device/instrumented). It is kept off `androidHostTest` on purpose — that target has no
 * Android framework, so `runComposeUiTest` NPEs there; the Android half of the suite runs
 * on-device instead. See docs/architecture/ui-test-dsl.md.
 */
@OptIn(ExperimentalTestApi::class)
class SmokeTest {
    @Test
    fun hostsAComposableAndFindsItByText() = runComposeUiTest {
        setContent {
            Text("hello-uitest")
        }

        onNodeWithText("hello-uitest").assertIsDisplayed()
    }
}
