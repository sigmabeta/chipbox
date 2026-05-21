package net.sigmabeta.chipbox.jvm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.strings.LocalChipboxStringProvider
import net.sigmabeta.chipbox.ui.theme.ChipboxTheme
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxFontDefaults

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. Wraps the Voyager
 * [Navigator] in (a) [ChipboxTheme] for color/typography/fonts, and (b) a
 * [CompositionLocalProvider] supplying [LocalMetroViewModelFactory] so each
 * [cafe.adriel.voyager.core.screen.Screen]'s `Content()` can call
 * `metroViewModel<...>()` without knowing it's on Desktop.
 *
 * The root destination is [HomeScreen]; that screen pushes [AboutScreen]; AboutScreen pops
 * back. Real feature screens slot into the same shape — `data object` or `data class` per
 * route, `Screen` interface, `LocalNavigator.currentOrThrow` for stack manipulation.
 */
@Composable
private fun DesktopApp() {
    val brand = ChipboxFontDefaults.Brand
    val plain = ChipboxFontDefaults.Plain
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor,
        plainScale = plain.scaleFactor,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Navigator(HomeScreen)
        }
    }
}

fun runDesktop(graph: JvmChipboxGraph) = application {
    Window(onCloseRequest = ::exitApplication, title = "Chipbox") {
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides graph.metroViewModelFactory,
            LocalChipboxStringProvider provides graph.stringProvider,
        ) {
            DesktopApp()
        }
    }
}
