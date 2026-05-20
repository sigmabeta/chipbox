@file:Suppress("MagicNumber") // hex color literals

package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. Proves the
 * Kotlin Compose compiler + JetBrains Compose runtime/material3/ui chain runs end-to-end
 * without committing to any of the bigger architectural decisions still in the air
 * (navigation library, ViewModel scoping on desktop, image-loading story, strings story).
 *
 * The theme is inlined on purpose. A follow-up slice will move the real
 * `cbox/android/ui/theme/api` colors/fonts into a shared KMP module and this file will
 * collapse into a thin call to that theme + the real `appui` root.
 */
private val BootstrapDark = darkColorScheme(
    primary = Color(0xFFEBB2FF),
    background = Color(0xFF1E1B1E),
    onBackground = Color(0xFFE8E0E5),
    surface = Color(0xFF1E1B1E),
    onSurface = Color(0xFFE8E0E5),
)

@Composable
private fun HelloChipbox() {
    MaterialTheme(colorScheme = BootstrapDark) {
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
