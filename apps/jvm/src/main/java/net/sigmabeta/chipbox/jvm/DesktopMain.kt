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
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxFontDefaults

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. [ChipboxTheme] is
 * the shared KMP composable that wraps Material3 with the Chipbox color schemes and a
 * Chipbox-shaped typography; we now also pass the real pixel-art brand/plain fonts via
 * [ChipboxFontDefaults], loaded through Compose Multiplatform resources from
 * `cbox/common/ui/fonts/api`. Each font's `scaleFactor` is folded in so a tall pixel font
 * doesn't dwarf a short one (same logic the Android `AppTheme` applies).
 */
@Composable
private fun HelloChipbox() {
    val brand = ChipboxFontDefaults.Brand
    val plain = ChipboxFontDefaults.Plain
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor,
        plainScale = plain.scaleFactor,
    ) {
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
