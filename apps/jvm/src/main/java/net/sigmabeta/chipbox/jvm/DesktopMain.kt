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
import net.sigmabeta.chipbox.jvm.di.JvmChipboxComponent
import net.sigmabeta.chipbox.ui.theme.ChipboxTheme
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxFontDefaults
import net.sigmabeta.chipbox.ui.vm.LocalViewModelProvider

/**
 * Bootstrap Compose Multiplatform entry point for the JVM/desktop target. Wraps the Voyager
 * [Navigator] in (a) [ChipboxTheme] for color/typography/fonts, and (b) a
 * [CompositionLocalProvider] supplying the JVM-side [JvmViewModelProvider] so each
 * [cafe.adriel.voyager.core.screen.Screen]'s `Content()` can call
 * `chipboxViewModel<...>()` without knowing it's on Desktop.
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

fun runDesktop(component: JvmChipboxComponent) = application {
    val provider = JvmViewModelProvider(component)
    Window(onCloseRequest = ::exitApplication, title = "Chipbox") {
        CompositionLocalProvider(LocalViewModelProvider provides provider) {
            DesktopApp()
        }
    }
}
