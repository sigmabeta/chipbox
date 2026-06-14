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
 * Lives in `jvmTest`, not `commonTest`: the same `runComposeUiTest` body NPEs on the Android
 * host target, which needs an Android framework (Robolectric) the JVM target supplies natively.
 * Robolectric in turn needs a `@RunWith` runner annotation, which can't live in
 * `commonMain`/`commonTest`. So JVM desktop is the canonical rail for now; the Android-host
 * execution strategy is an open decision recorded in docs/architecture/ui-test-dsl.md.
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
