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
import net.sigmabeta.chipbox.ui.theme.ChipboxDark

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. The desktop
 * theme is just `MaterialTheme(colorScheme = ChipboxDark)` from the shared KMP color
 * module — the full Android `AppTheme()` wraps that same palette in `SageMaterial` plus
 * a typography pipeline that still depends on Android font resources; both move when
 * the font-resource story is settled (likely Compose-MP resources or expect/actual).
 */
@Composable
private fun HelloChipbox() {
    MaterialTheme(colorScheme = ChipboxDark) {
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
