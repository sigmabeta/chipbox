package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.sigmabeta.chipbox.ui.theme.ChipboxTheme

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. [ChipboxTheme] is
 * the shared KMP composable that wraps Material3 with the Chipbox color schemes and a
 * Chipbox-shaped typography. The brand/plain `FontFamily`s default to `FontFamily.Default`
 * here — Chipbox's pixel-art fonts live as Android resources today, and the font-resource
 * story for desktop (Compose-MP resources vs `expect`/`actual` `FontFamily`) hasn't been
 * picked yet. The typography *structure* (sizes / weights / line heights) is already shared.
 */
@Composable
private fun HelloChipbox() {
    ChipboxTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Hello, Chipbox",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
    }
}

fun runDesktop() = application {
    Window(onCloseRequest = ::exitApplication, title = "Chipbox") {
        HelloChipbox()
    }
}
